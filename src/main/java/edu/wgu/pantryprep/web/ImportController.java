package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.importing.ImportReport;
import edu.wgu.pantryprep.importing.ImportWorkbooks;
import edu.wgu.pantryprep.importing.SpreadsheetImportService;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.UserService;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Serializable;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Spreadsheet import. The cook downloads one template with sheets for
 * recipes, pantry items and ingredients, fills in whichever they need, and
 * uploads it for a preview; the file is then held in their session until they
 * import it or cancel. Importing re-reads the held file and saves everything
 * together, so what is saved is exactly what the checks passed, even if
 * something changed since the preview.
 */
@Controller
public class ImportController {

    /** Larger files are refused before they are read. */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    static final String PENDING = "pantryprep.pendingImport";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** An uploaded file waiting for the cook to import it or cancel. */
    record PendingImport(String token, String fileName, byte[] bytes, Long userId, ImportReport preview)
            implements Serializable {
    }

    private final SpreadsheetImportService imports;
    private final UserService userService;

    public ImportController(SpreadsheetImportService imports, UserService userService) {
        this.imports = imports;
        this.userService = userService;
    }

    @GetMapping("/import")
    public String page(@AuthenticationPrincipal AppUserDetails principal, HttpSession session, Model model) {
        model.addAttribute("workbook", ImportWorkbooks.WORKBOOK);
        model.addAttribute("templateName", ImportWorkbooks.WORKBOOK.fileName());
        model.addAttribute("maxMegabytes", MAX_FILE_BYTES / (1024 * 1024));
        PendingImport pending = pending(session, principal);
        if (pending != null) {
            model.addAttribute("pending", pending);
            model.addAttribute("report", pending.preview());
        }
        return "imports/import";
    }

    /** The two pages this replaced, kept so old bookmarks still land somewhere useful. */
    @GetMapping({"/recipes/import", "/pantry/import"})
    public String oldPages() {
        return "redirect:/import";
    }

    @GetMapping("/import/template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ImportWorkbooks.WORKBOOK.fileName()).build()
                                .toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(imports.template());
    }

    @PostMapping("/import/preview")
    public String preview(@RequestParam(name = "file", required = false) MultipartFile file,
                          @AuthenticationPrincipal AppUserDetails principal,
                          HttpSession session, RedirectAttributes redirect) throws IOException {
        session.removeAttribute(PENDING);
        String refusal = refusal(file);
        if (refusal != null) {
            redirect.addFlashAttribute("error", refusal);
            return "redirect:/import";
        }
        byte[] bytes = file.getBytes();
        ImportReport report = imports.preview(bytes, currentUser(principal));
        if (report.hasProblems()) {
            redirect.addFlashAttribute("report", report);
            redirect.addFlashAttribute("fileName", displayName(file));
        } else {
            session.setAttribute(PENDING, new PendingImport(newToken(), displayName(file), bytes,
                    principal.getId(), report));
        }
        return "redirect:/import";
    }

    @PostMapping("/import/confirm")
    public String confirm(@RequestParam("token") String token, @AuthenticationPrincipal AppUserDetails principal,
                          HttpSession session, RedirectAttributes redirect) {
        PendingImport pending = pending(session, principal);
        if (pending == null || !pending.token().equals(token)) {
            redirect.addFlashAttribute("error", "That upload has expired. Upload the spreadsheet again to "
                    + "import it.");
            return "redirect:/import";
        }
        ImportReport report = imports.importFile(pending.bytes(), currentUser(principal));
        session.removeAttribute(PENDING);
        if (!report.saved()) {
            redirect.addFlashAttribute("report", report);
            redirect.addFlashAttribute("fileName", pending.fileName());
            return "redirect:/import";
        }
        redirect.addFlashAttribute("message", "Imported " + report.summary() + ".");
        redirect.addFlashAttribute("celebrate", true);
        return "redirect:" + landing(report);
    }

    @PostMapping("/import/cancel")
    public String cancel(HttpSession session) {
        session.removeAttribute(PENDING);
        return "redirect:/import";
    }

    // ------------------------------------------------------------- helpers

    /** Where to show the result: the first list the import added to. */
    static String landing(ImportReport report) {
        if (!report.recipes().isEmpty()) {
            return "/recipes";
        }
        if (report.pantryItems() > 0) {
            return "/pantry";
        }
        return "/ingredients";
    }

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    /** The waiting upload for this account, or null. */
    private static PendingImport pending(HttpSession session, AppUserDetails principal) {
        if (session.getAttribute(PENDING) instanceof PendingImport pending
                && pending.userId().equals(principal.getId())) {
            return pending;
        }
        return null;
    }

    /** Why this upload can't be read, or null when it can. */
    static String refusal(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "Choose a spreadsheet to upload first.";
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return tooLargeMessage();
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xls") || name.endsWith(".csv") || name.endsWith(".numbers") || name.endsWith(".ods")
                || name.endsWith(".xlsm")) {
            return "PantryPrep reads .xlsx files. Open this one in your spreadsheet app, save it as an Excel "
                    + "workbook (.xlsx) and upload that.";
        }
        return null;
    }

    static String tooLargeMessage() {
        return "That file is larger than " + (MAX_FILE_BYTES / (1024 * 1024)) + " MB. A filled-in template is "
                + "far smaller, so check it's the right file.";
    }

    private static String displayName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "your spreadsheet";
        }
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.length() > 80 ? base.substring(0, 77) + "..." : base;
    }

    private static String newToken() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}

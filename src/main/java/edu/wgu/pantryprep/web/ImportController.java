package edu.wgu.pantryprep.web;

import edu.wgu.pantryprep.domain.User;
import edu.wgu.pantryprep.importing.ImportReport;
import edu.wgu.pantryprep.importing.ImportWorkbooks;
import edu.wgu.pantryprep.importing.PantryImportService;
import edu.wgu.pantryprep.importing.RecipeImportService;
import edu.wgu.pantryprep.importing.WorkbookSpec;
import edu.wgu.pantryprep.security.AppUserDetails;
import edu.wgu.pantryprep.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
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
 * Spreadsheet import for recipes and for the pantry. The cook downloads a
 * template, fills it in, and uploads it for a preview; the file is then held
 * in their session until they import it or cancel. Importing re-reads the
 * held file and saves everything together, so what is saved is exactly what
 * the checks passed, even if something changed since the preview.
 */
@Controller
public class ImportController {

    /** Larger files are refused before they are read. */
    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    static final String PENDING = "pantryprep.pendingImport";
    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Which of the two imports a page is for. */
    public enum Kind {
        RECIPES("recipes", "recipes", "Import recipes", ImportWorkbooks.RECIPE_WORKBOOK),
        PANTRY("pantry", "pantry items", "Import pantry items", ImportWorkbooks.PANTRY_WORKBOOK);

        private final String path;
        private final String noun;
        private final String title;
        private final WorkbookSpec workbook;

        Kind(String path, String noun, String title, WorkbookSpec workbook) {
            this.path = path;
            this.noun = noun;
            this.title = title;
            this.workbook = workbook;
        }

        public String getPath() {
            return path;
        }

        public String getNoun() {
            return noun;
        }

        public String getTitle() {
            return title;
        }

        public WorkbookSpec getWorkbook() {
            return workbook;
        }

        public String getTemplateName() {
            return "pantryprep-" + path + "-template.xlsx";
        }

        /** From the address: everything under /pantry is the pantry import. */
        static Kind of(HttpServletRequest request) {
            return request.getRequestURI().startsWith(request.getContextPath() + "/pantry/") ? PANTRY : RECIPES;
        }
    }

    /** An uploaded file waiting for the cook to import it or cancel. */
    record PendingImport(String token, Kind kind, String fileName, byte[] bytes, Long userId,
                         ImportReport preview) implements Serializable {
    }

    private final RecipeImportService recipeImports;
    private final PantryImportService pantryImports;
    private final UserService userService;

    public ImportController(RecipeImportService recipeImports, PantryImportService pantryImports,
                            UserService userService) {
        this.recipeImports = recipeImports;
        this.pantryImports = pantryImports;
        this.userService = userService;
    }

    @GetMapping({"/recipes/import", "/pantry/import"})
    public String page(HttpServletRequest request, @AuthenticationPrincipal AppUserDetails principal,
                       HttpSession session, Model model) {
        Kind kind = Kind.of(request);
        model.addAttribute("kind", kind);
        model.addAttribute("maxMegabytes", MAX_FILE_BYTES / (1024 * 1024));
        PendingImport pending = pending(session, kind, principal);
        if (pending != null) {
            model.addAttribute("pending", pending);
            model.addAttribute("report", pending.preview());
        }
        return "imports/import";
    }

    @GetMapping({"/recipes/import/template", "/pantry/import/template"})
    public ResponseEntity<byte[]> template(HttpServletRequest request) {
        Kind kind = Kind.of(request);
        byte[] bytes = kind == Kind.PANTRY ? pantryImports.template() : recipeImports.template();
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(kind.getTemplateName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }

    @PostMapping({"/recipes/import/preview", "/pantry/import/preview"})
    public String preview(HttpServletRequest request,
                          @RequestParam(name = "file", required = false) MultipartFile file,
                          @AuthenticationPrincipal AppUserDetails principal,
                          HttpSession session, RedirectAttributes redirect) throws IOException {
        Kind kind = Kind.of(request);
        session.removeAttribute(PENDING);
        String refusal = refusal(file);
        if (refusal != null) {
            redirect.addFlashAttribute("error", refusal);
            return "redirect:/" + kind.getPath() + "/import";
        }
        byte[] bytes = file.getBytes();
        User user = currentUser(principal);
        ImportReport report = kind == Kind.PANTRY ? pantryImports.preview(bytes, user)
                : recipeImports.preview(bytes, user);
        if (report.hasProblems()) {
            redirect.addFlashAttribute("report", report);
            redirect.addFlashAttribute("fileName", displayName(file));
        } else {
            session.setAttribute(PENDING, new PendingImport(newToken(), kind, displayName(file), bytes,
                    principal.getId(), report));
        }
        return "redirect:/" + kind.getPath() + "/import";
    }

    @PostMapping({"/recipes/import/confirm", "/pantry/import/confirm"})
    public String confirm(HttpServletRequest request, @RequestParam("token") String token,
                          @AuthenticationPrincipal AppUserDetails principal,
                          HttpSession session, RedirectAttributes redirect) {
        Kind kind = Kind.of(request);
        PendingImport pending = pending(session, kind, principal);
        if (pending == null || !pending.token().equals(token)) {
            redirect.addFlashAttribute("error", "That upload has expired. Upload the spreadsheet again to "
                    + "import it.");
            return "redirect:/" + kind.getPath() + "/import";
        }
        User user = currentUser(principal);
        ImportReport report = kind == Kind.PANTRY ? pantryImports.importFile(pending.bytes(), user)
                : recipeImports.importFile(pending.bytes(), user);
        session.removeAttribute(PENDING);
        if (!report.saved()) {
            redirect.addFlashAttribute("report", report);
            redirect.addFlashAttribute("fileName", pending.fileName());
            return "redirect:/" + kind.getPath() + "/import";
        }
        redirect.addFlashAttribute("message", summary(kind, report));
        redirect.addFlashAttribute("celebrate", true);
        return "redirect:/" + kind.getPath();
    }

    @PostMapping({"/recipes/import/cancel", "/pantry/import/cancel"})
    public String cancel(HttpServletRequest request, HttpSession session) {
        session.removeAttribute(PENDING);
        return "redirect:/" + Kind.of(request).getPath() + "/import";
    }

    // ------------------------------------------------------------- helpers

    private User currentUser(AppUserDetails principal) {
        return userService.requireById(principal.getId());
    }

    /** The waiting upload for this page and this account, or null. */
    private static PendingImport pending(HttpSession session, Kind kind, AppUserDetails principal) {
        if (session.getAttribute(PENDING) instanceof PendingImport pending
                && pending.kind() == kind && pending.userId().equals(principal.getId())) {
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

    static String summary(Kind kind, ImportReport report) {
        int count = kind == Kind.PANTRY ? report.counts().getFirst().rows() : report.created().size();
        String what = kind == Kind.PANTRY ? (count == 1 ? "pantry item" : "pantry items")
                : (count == 1 ? "recipe" : "recipes");
        StringBuilder text = new StringBuilder("Imported ").append(count).append(' ').append(what);
        int fresh = report.newIngredients().size();
        if (fresh > 0) {
            text.append(" and added ").append(fresh).append(fresh == 1 ? " new ingredient" : " new ingredients");
        }
        return text.append('.').toString();
    }
}

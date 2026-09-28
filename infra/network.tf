# One public subnet and no NAT gateway. The instance has an Elastic IP and
# reaches the internet directly, which keeps the network cost at zero.

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "pantryplan-prod" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "pantryplan-prod" }
}

resource "aws_subnet" "public" {
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 1)
  availability_zone = data.aws_availability_zones.available.names[0]

  tags = { Name = "pantryplan-prod-public" }

  # The data volume lives in this AZ. Never let a changed AZ list move it.
  lifecycle {
    ignore_changes = [availability_zone]
  }
}

# Strip all rules from the VPC's default security group so nothing uses it by accident.
resource "aws_default_security_group" "default" {
  vpc_id = aws_vpc.main.id
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "pantryplan-prod-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Web traffic only. No SSH port: shell access goes through SSM Session Manager.
resource "aws_security_group" "web" {
  name        = "pantryplan-prod-web"
  description = "HTTP and HTTPS to the PantryPlan host"
  vpc_id      = aws_vpc.main.id
}

resource "aws_vpc_security_group_ingress_rule" "http" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP, redirected to HTTPS by Caddy"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "https" {
  security_group_id = aws_security_group.web.id
  description       = "HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "http3" {
  security_group_id = aws_security_group.web.id
  description       = "HTTP/3"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "udp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.web.id
  description       = "Package installs, image pulls, SSM, Lets Encrypt"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

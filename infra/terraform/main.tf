terraform {
  # 로컬에서 계획을 확인하고 수동으로 적용하는 배포 방식이므로 원격 backend는 사용하지 않는다.
  required_version = ">= 1.6.0"

  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 8.0"
    }
  }
}

# OCI API 키 인증 정보는 secret.tf에 선언한 변수로 주입한다.
provider "oci" {
  tenancy_ocid     = var.tenancy_ocid
  user_ocid        = var.user_ocid
  fingerprint      = var.fingerprint
  private_key_path = var.private_key_path
  region           = var.region
}

# AD를 직접 지정하지 않은 경우 해당 tenancy의 첫 번째 AD를 사용한다.
data "oci_identity_availability_domains" "this" {
  compartment_id = var.tenancy_ocid
}

# image_ocid가 비어 있을 때만 Ubuntu 24.04 이미지를 조회한다.
data "oci_core_images" "ubuntu" {
  count                    = var.image_ocid == null || var.image_ocid == "" ? 1 : 0
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = var.instance_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

locals {
  name_prefix = "${var.project_name}-${var.environment}"

  availability_domain = var.availability_domain != null && var.availability_domain != "" ? var.availability_domain : data.oci_identity_availability_domains.this.availability_domains[0].name

  selected_image_ocid = var.image_ocid != null && var.image_ocid != "" ? var.image_ocid : data.oci_core_images.ubuntu[0].images[0].id

  common_freeform_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # 외부에 공개할 포트는 웹/SSH만이다. PostgreSQL(5432)과 Spring(8080)은 공개하지 않는다.
  public_ingress_ports = {
    ssh = {
      port        = 22
      description = "운영 VM SSH 접속"
    }
    http = {
      port        = 80
      description = "HTTP 리버스 프록시"
    }
    https = {
      port        = 443
      description = "HTTPS 리버스 프록시"
    }
  }
}

# ------------------------------
# 전용 네트워크
# ------------------------------

resource "oci_core_vcn" "this" {
  compartment_id = var.compartment_ocid
  cidr_blocks    = [var.vcn_cidr_block]
  display_name   = "${local.name_prefix}-vcn"
  dns_label      = "techview"

  freeform_tags = local.common_freeform_tags
}

resource "oci_core_internet_gateway" "this" {
  compartment_id = var.compartment_ocid
  display_name   = "${local.name_prefix}-igw"
  enabled        = true
  vcn_id         = oci_core_vcn.this.id

  freeform_tags = local.common_freeform_tags
}

resource "oci_core_route_table" "public" {
  compartment_id = var.compartment_ocid
  display_name   = "${local.name_prefix}-public-route-table"
  vcn_id         = oci_core_vcn.this.id

  route_rules {
    destination       = "0.0.0.0/0"
    destination_type  = "CIDR_BLOCK"
    network_entity_id = oci_core_internet_gateway.this.id
  }

  freeform_tags = local.common_freeform_tags
}

# Security List는 모든 outbound 연결을 허용하고, inbound는 NSG에서만 관리한다.
resource "oci_core_security_list" "public" {
  compartment_id = var.compartment_ocid
  display_name   = "${local.name_prefix}-public-security-list"
  vcn_id         = oci_core_vcn.this.id

  egress_security_rules {
    destination      = "0.0.0.0/0"
    destination_type = "CIDR_BLOCK"
    protocol         = "all"
    stateless        = false
  }

  freeform_tags = local.common_freeform_tags
}

# NSG는 VM의 실제 서비스 경계를 표현한다. 22/80/443 외의 inbound 규칙은 만들지 않는다.
resource "oci_core_network_security_group" "this" {
  compartment_id = var.compartment_ocid
  display_name   = "${local.name_prefix}-nsg"
  vcn_id         = oci_core_vcn.this.id

  freeform_tags = local.common_freeform_tags
}

resource "oci_core_network_security_group_security_rule" "public_ingress" {
  for_each = local.public_ingress_ports

  network_security_group_id = oci_core_network_security_group.this.id
  direction                 = "INGRESS"
  protocol                  = "6"
  source                    = "0.0.0.0/0"
  source_type               = "CIDR_BLOCK"
  stateless                 = false
  description               = each.value.description

  tcp_options {
    destination_port_range {
      min = each.value.port
      max = each.value.port
    }
  }
}

resource "oci_core_network_security_group_security_rule" "public_egress" {
  network_security_group_id = oci_core_network_security_group.this.id
  direction                 = "EGRESS"
  protocol                  = "all"
  destination               = "0.0.0.0/0"
  destination_type          = "CIDR_BLOCK"
  stateless                 = false
  description               = "외부 API 및 패키지 저장소로 나가는 outbound 연결"
}

resource "oci_core_subnet" "public" {
  compartment_id             = var.compartment_ocid
  cidr_block                 = var.public_subnet_cidr_block
  display_name               = "${local.name_prefix}-public-subnet"
  dns_label                  = "public"
  prohibit_public_ip_on_vnic = false
  route_table_id             = oci_core_route_table.public.id
  security_list_ids          = [oci_core_security_list.public.id]
  vcn_id                     = oci_core_vcn.this.id

  freeform_tags = local.common_freeform_tags
}

# ------------------------------
# 애플리케이션 VM과 고정 공인 IP
# ------------------------------

resource "oci_core_instance" "app" {
  availability_domain = local.availability_domain
  compartment_id      = var.compartment_ocid
  display_name        = var.instance_display_name
  shape               = var.instance_shape

  shape_config {
    ocpus         = var.instance_ocpus
    memory_in_gbs = var.instance_memory_in_gbs
  }

  create_vnic_details {
    # Reserved Public IP를 별도 리소스로 연결하므로 여기서는 ephemeral IP를 만들지 않는다.
    assign_public_ip = false
    hostname_label   = var.hostname_label
    nsg_ids          = [oci_core_network_security_group.this.id]
    subnet_id        = oci_core_subnet.public.id
  }

  source_details {
    boot_volume_size_in_gbs = var.boot_volume_size_in_gbs
    source_id               = local.selected_image_ocid
    source_type             = "image"
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data           = base64encode(file("${path.module}/vm-init.sh"))
  }

  freeform_tags = local.common_freeform_tags
}

# Instance의 primary VNIC와 private IP OCID를 조회해 Reserved Public IP에 연결한다.
data "oci_core_vnic_attachments" "app" {
  compartment_id = var.compartment_ocid
  instance_id    = oci_core_instance.app.id
}

data "oci_core_private_ips" "app" {
  vnic_id = data.oci_core_vnic_attachments.app.vnic_attachments[0].vnic_id
}

resource "oci_core_public_ip" "reserved" {
  compartment_id = var.compartment_ocid
  display_name   = "${local.name_prefix}-reserved-public-ip"
  lifetime       = "RESERVED"
  private_ip_id  = data.oci_core_private_ips.app.private_ips[0].id

  freeform_tags = local.common_freeform_tags
}

# ------------------------------
# 임시 음성 파일용 private Object Storage bucket
# ------------------------------

resource "oci_objectstorage_bucket" "audio" {
  compartment_id = var.compartment_ocid
  name           = var.bucket_name
  namespace      = var.object_storage_namespace
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Disabled"

  freeform_tags = local.common_freeform_tags
}

# 애플리케이션 정리 작업이 누락된 경우에도 임시 음성이 장기 보관되지 않도록 하는 안전망이다.
# OCI lifecycle은 일 단위로 평가되므로 실제 삭제에는 최대 24시간가량이 더 걸릴 수 있다.
resource "oci_objectstorage_object_lifecycle_policy" "audio" {
  namespace = var.object_storage_namespace
  bucket    = oci_objectstorage_bucket.audio.name

  # 새 환경에서는 Object Storage 서비스 권한이 먼저 생성되도록 실행 순서를 보장한다.
  depends_on = [oci_identity_policy.storage_service]

  rules {
    action      = "DELETE"
    is_enabled  = true
    name        = "delete-temporary-audio-after-one-day"
    target      = "objects"
    time_amount = 1
    time_unit   = "DAYS"
  }
}

# ------------------------------
# Object Storage 전용 최소 권한 IAM
# ------------------------------

# 운영 애플리케이션의 Customer Secret Key 소유자다.
# 실제 키 쌍은 Terraform state에 남기지 않기 위해 OCI 콘솔에서 수동 발급한다.
resource "oci_identity_user" "storage_service" {
  compartment_id = var.tenancy_ocid
  name           = "${local.name_prefix}-storage-service"
  description    = "TechView backend S3 compatibility credentials owner"
  email          = var.storage_service_user_email

  freeform_tags = local.common_freeform_tags
}

resource "oci_identity_group" "storage" {
  compartment_id = var.tenancy_ocid
  name           = "${local.name_prefix}-storage"
  description    = "TechView audio Object Storage access group"

  freeform_tags = local.common_freeform_tags
}

resource "oci_identity_user_group_membership" "storage_service" {
  group_id = oci_identity_group.storage.id
  user_id  = oci_identity_user.storage_service.id
}

# 버킷 목록 등 compartment 전체 권한은 주지 않고 techview-audio 버킷의 조회와 객체 관리만 허용한다.
resource "oci_identity_policy" "storage_service" {
  compartment_id = var.tenancy_ocid
  name           = "${local.name_prefix}-storage"
  description    = "Allow the TechView storage service user to use only the audio bucket"

  statements = [
    "Allow group id ${oci_identity_group.storage.id} to read buckets in compartment id ${var.compartment_ocid} where target.bucket.name = '${var.bucket_name}'",
    "Allow group id ${oci_identity_group.storage.id} to manage objects in compartment id ${var.compartment_ocid} where target.bucket.name = '${var.bucket_name}'",
    "Allow service objectstorage-${var.region} to manage object-family in compartment id ${var.compartment_ocid} where target.bucket.name = '${var.bucket_name}'"
  ]

  freeform_tags = local.common_freeform_tags
}

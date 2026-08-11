variable "project_name" {
  description = "OCI 리소스 이름과 태그에 사용할 프로젝트 이름"
  type        = string
  default     = "techview"
}

variable "environment" {
  description = "배포 환경 이름"
  type        = string
  default     = "prod"
}

variable "region" {
  description = "OCI 리전을 지정한다. Object Storage와 VM은 같은 리전을 사용한다."
  type        = string
}

variable "compartment_ocid" {
  description = "VM, 네트워크, 버킷을 생성할 OCI compartment OCID"
  type        = string
}

variable "availability_domain" {
  description = "사용할 availability domain 이름. 비워두면 첫 번째 AD를 사용한다."
  type        = string
  default     = null
  nullable    = true
}

variable "vcn_cidr_block" {
  description = "전용 VCN의 IPv4 CIDR"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidr_block" {
  description = "VM이 배치될 public subnet의 IPv4 CIDR"
  type        = string
  default     = "10.0.1.0/24"
}

variable "instance_shape" {
  description = "애플리케이션 VM shape"
  type        = string
  default     = "VM.Standard.A1.Flex"
}

variable "instance_ocpus" {
  description = "Flex VM에 할당할 OCPU 수"
  type        = number
  default     = 2

  validation {
    condition     = var.instance_ocpus > 0
    error_message = "instance_ocpus는 0보다 커야 합니다."
  }
}

variable "instance_memory_in_gbs" {
  description = "Flex VM에 할당할 메모리(GB)"
  type        = number
  default     = 8

  validation {
    condition     = var.instance_memory_in_gbs > 0
    error_message = "instance_memory_in_gbs는 0보다 커야 합니다."
  }
}

variable "boot_volume_size_in_gbs" {
  description = "VM boot volume 크기(GB)"
  type        = number
  default     = 50

  validation {
    condition     = var.boot_volume_size_in_gbs >= 50
    error_message = "boot_volume_size_in_gbs는 최소 50GB여야 합니다."
  }
}

variable "instance_display_name" {
  description = "애플리케이션 VM 표시 이름"
  type        = string
  default     = "techview-app"
}

variable "hostname_label" {
  description = "VM primary VNIC hostname label"
  type        = string
  default     = "techview-app"
}

variable "ssh_public_key" {
  description = "VM 접속에 사용할 SSH 공개 키"
  type        = string
}

variable "image_ocid" {
  description = "사용할 이미지 OCID. null이면 Ubuntu 24.04 이미지를 자동 조회한다."
  type        = string
  default     = null
  nullable    = true
}

variable "object_storage_namespace" {
  description = "OCI Object Storage namespace"
  type        = string
}

variable "bucket_name" {
  description = "임시 음성 파일을 저장할 private Object Storage bucket 이름"
  type        = string
  default     = "techview-audio"
}

variable "storage_service_user_email" {
  description = "Object Storage Customer Secret Key를 소유할 서비스 사용자의 기본 이메일"
  type        = string
  sensitive   = true

  validation {
    condition     = trimspace(var.storage_service_user_email) != ""
    error_message = "storage_service_user_email은 비어 있을 수 없습니다."
  }
}

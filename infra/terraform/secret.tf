# 이 파일에는 secret 값을 기록하지 않는다.
# 실제 값은 로컬에서 terraform.tfvars(커밋 금지) 또는 환경 변수로 주입한다.

variable "tenancy_ocid" {
  description = "OCI tenancy OCID"
  type        = string
  sensitive   = true
}

variable "user_ocid" {
  description = "Terraform을 실행할 OCI 사용자 OCID"
  type        = string
  sensitive   = true
}

variable "fingerprint" {
  description = "OCI API 키 fingerprint"
  type        = string
  sensitive   = true
}

variable "private_key_path" {
  description = "OCI API 키 private key 파일 경로"
  type        = string
  sensitive   = true
}

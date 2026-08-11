output "vcn_id" {
  description = "TechView 전용 VCN OCID"
  value       = oci_core_vcn.this.id
}

output "public_subnet_id" {
  description = "애플리케이션 VM이 배치된 public subnet OCID"
  value       = oci_core_subnet.public.id
}

output "instance_id" {
  description = "애플리케이션 VM OCID"
  value       = oci_core_instance.app.id
}

output "instance_private_ip" {
  description = "애플리케이션 VM primary private IP"
  value       = oci_core_instance.app.private_ip
}

output "reserved_public_ip" {
  description = "VM primary private IP에 연결된 Reserved Public IP"
  value       = oci_core_public_ip.reserved.ip_address
}

output "object_storage_namespace" {
  description = "음성 bucket의 Object Storage namespace"
  value       = var.object_storage_namespace
}

output "audio_bucket_name" {
  description = "임시 음성 파일 bucket 이름"
  value       = oci_objectstorage_bucket.audio.name
}

output "region" {
  description = "배포 리전"
  value       = var.region
}

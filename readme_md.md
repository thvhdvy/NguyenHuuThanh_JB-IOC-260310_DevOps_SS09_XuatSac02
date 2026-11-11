# Hệ Thống Pipeline CI/CD Cho Microservice "User Service"

Tài liệu này chi tiết hóa quy trình thiết lập tự động hóa CI/CD cho **User Service** (Spring Boot + Gradle + PostgreSQL) trên nền tảng **GitHub Actions**. Hệ thống đảm bảo kiểm thử tích hợp (Integration Test) nghiêm ngặt với cơ sở dữ liệu thực tế trước khi thực hiện đóng gói và gắn tag phiên bản tự động bằng `github.sha` đẩy lên Docker Hub.

---

## 1. Tổng Quan Hệ Thống

* **Ứng dụng:** User Service (Spring Boot 3.x, Java 17, Gradle Wrapper).
* **Cơ sở dữ liệu:** PostgreSQL 14 (Chạy dưới dạng Service Container trong GitHub Actions Runner).
* **Cơ chế đóng gói:** Docker Multi-stage Build (Tối ưu dung lượng image < 200MB, bảo mật nguồn mã nguồn).
* **Nền tảng CI/CD:** GitHub Actions (`.github/workflows/ci.yml`).
* **Chiến lược Quản lý Phiên bản (Versioning):** Gắn tag hình ảnh Docker theo mã Commit SHA ngắn (`${GITHUB_SHA::7}`) để đảm bảo tính định danh và hỗ trợ truy xuất/rollback phiên bản khi có sự cố.

---

## 2. Sơ Đồ Luồng Pipeline (Pipeline Architecture)

Pipeline được chia làm 3 jobs chính liên kết chặt chẽ (luồng phụ thuộc tuần tự). Nếu bước trước thất bại, toàn bộ tiến trình phía sau sẽ bị hủy ngay lập tức (*Fail-fast policy*).

```text
[ Push / Pull Request to main/master ] 
                   │
                   ▼
       ┌──────────────────────┐      Thất bại
       │    Job 1: TEST       │ ─────────────────► [ Dừng Pipeline ]
       │ (PostgreSQL Service) │                    (Không tạo Artifact / Không build Docker)
       └───────────┬──────────┘
                   │ Passed
                   ▼
       ┌──────────────────────┐      Thất bại
       │    Job 2: BUILD      │ ─────────────────► [ Dừng Pipeline ]
       │ (Tạo & Lưu .jar)     │
       └───────────┬──────────┘
                   │ Passed (needs: test)
                   ▼
       ┌──────────────────────┐
       │  Job 3: DOCKERIZE    │ ─────────────────► [ Push Docker Image ]
       │ (Multi-stage Build)  │                    (Tag: user-service:<commit_sha>)
       └──────────────────────┘
```

---

## 3. Hướng Dẫn Thiết Lập Cấu Hình Bảo Mật (GitHub Secrets)

Để đảm bảo an toàn thông tin đăng nhập, bạn cần cấu hình các biến bảo mật tại mục **Settings -> Secrets and variables -> Actions -> Repository secrets** trên repository GitHub:

| Tên Secret (Key) | Giá Trị Mẫu | Mục Đích Sử Dụng |
| :--- | :--- | :--- |
| `DOCKER_USERNAME` | `your-docker-username` | Tên tài khoản đăng nhập Docker Hub. |
| `DOCKER_PASSWORD` | `dapp_xxxxxxxxxxxx` | Personal Access Token (PAT) được tạo từ Docker Hub. |
| `POSTGRES_DB` | `user_db_test` | Tên Database dùng cho bước Integration Test. |
| `POSTGRES_USER` | `user_admin` | Tài khoản truy cập PostgreSQL Container. |
| `POSTGRES_PASSWORD` | `user_secret_pass` | Mật khẩu truy cập PostgreSQL Container. |

---


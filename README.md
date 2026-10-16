# 🧠 Veritas — Backend

Veritas is a **multi-agent AI decision system** built with **Spring Boot** that simulates a council of expert personas (Analyst, Strategist, Critic, Optimizer, Synthesizer) to generate **structured, high-quality answers** for complex questions.

This repository contains the **backend service** powering Veritas.

---

## 🚀 Features

### 🧩 Multi-Agent Architecture

- **Analyst** — breaks down the problem  
- **Strategist** — plans approaches  
- **Critic** — evaluates weaknesses  
- **Optimizer** — improves efficiency & clarity  
- **Synthesizer** — produces the final answer  

---

### 🤖 Local LLM via Ollama

- Runs fully on the local machine  
- No paid APIs required  
- Production-ready for self-hosting  

---

### 🔐 Authentication

- JWT-based authentication  
- Firebase Admin SDK integration  

---

### 🗄️ Persistence

- H2 database (file-based)  
- JPA / Hibernate  

---

### 🌐 REST API

- Clean controller–service–engine separation  
- Frontend-agnostic design  

---

## 🏗️ Tech Stack

### Backend

- Java 17  
- Spring Boot 3  
- Spring Security  
- Spring Data JPA  
- LangChain4j  
- Ollama (local LLM)  
- H2 Database  

### Auth & Infra

- Firebase Admin SDK  
- JWT (jjwt)  

---





## Security & quota settings

All optional; defaults shown.

| Env var | Default | Purpose |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `https://ai-council-one-livid.vercel.app` | Comma-separated frontend origins. Add `http://localhost:5173` for local dev. |
| `RATE_LIMIT_MAX_PER_WINDOW` / `RATE_LIMIT_WINDOW_SECONDS` | `5` / `60` | Burst limit per user. |
| `RATE_LIMIT_PER_USER_DAILY` | `25` | Questions per user per UTC day. |
| `RATE_LIMIT_GLOBAL_DAILY` | `200` | Questions per UTC day for the whole site (protects free LLM quota). |
| `COUNCIL_TIME_BUDGET_SECONDS` | `150` | Hard cap on one council run. |
| `OTP_MAX_EMAILS_PER_DAY` | `300` | Verification emails per UTC day, site-wide (Gmail limit is ~500). |

Verified emails are written to Firebase (`emailVerified`), so they survive
redeploys; the local H2 database only holds short-lived OTP codes.

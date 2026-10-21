# ADR-002: Angular Standalone SPA & Spring Boot 3.3 Backend
**Status:** APPROVED  
**Date:** 2026-10-21  

### Context and Problem Statement
The application requires a fast, touch-friendly kiosk UI capable of accessing browser media devices (webcam) without lag, alongside a rich, reactive administrator dashboard.

### Options Considered
1. **Server-Side Rendered (Thymeleaf / Spring MVC):** Server renders HTML; limited native client webcam stream control.
2. **React SPA + Node.js Backend:** High flexibility, but fragmented ecosystem and weaker enterprise typing compared to Angular/Java.
3. **Angular 18+ Standalone SPA + Spring Boot 3.3 (Java 21):** Strong enterprise typing end-to-end, compile-time safety, built-in dependency injection, and mature Spring ecosystem.

### Selected Decision
Option 3: **Angular 18+ Standalone Components + Spring Boot 3.3**.

### Reasons for Selection
- Angular's standalone component model eliminates NgModule boilerplate and supports fine-grained lazy loading.
- Browser MediaDevices API integration is encapsulated cleanly in Angular components with explicit track teardown in `ngOnDestroy()`.
- Spring Boot 3.3 running on Java 21 LTS leverages Virtual Threads, Bean Validation, Spring Security 6, and mature connection pooling (HikariCP).

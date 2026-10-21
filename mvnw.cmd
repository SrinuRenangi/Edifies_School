@echo off
@REM Maven Wrapper for Staff Attendance Management System

set MAVEN_WRAPPER_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.9\8e74001100ff70d6af083c5511fcc5ec49282d7017cde82c3698eee8fdf86698\bin

if exist "%MAVEN_WRAPPER_DIR%\mvn.cmd" (
    call "%MAVEN_WRAPPER_DIR%\mvn.cmd" %*
) else (
    mvn %*
)

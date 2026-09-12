@echo off
rem 进销存 ERP Java 后端编译脚本
set M2_HOME=C:\maven\apache-maven-3.9.9
C:\maven\apache-maven-3.9.9\bin\mvn.cmd -f I:\erp-server\backend\pom.xml -DskipTests package %*

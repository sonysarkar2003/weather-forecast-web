@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM ----------------------------------------------------------------------------
@echo off
set ERROR_CODE=0

set MAVEN_CMD=.\.tools\apache-maven-3.9.6\bin\mvn.cmd
if exist %MAVEN_CMD% (
    %MAVEN_CMD% %*
) else (
    mvn %*
)

@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.2
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; others: silence the output
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_ERROR__=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN ("%~dp0\.mvn\wrapper\maven-wrapper.properties") DO @(
    IF "%%~A"=="wrapperUrl" SET "__MVNW_CMD__=%%~B"
    IF "%%~A"=="distributionUrl" SET "MVNW_distributionUrl=%%~B"
)
@IF "%MVNW_distributionUrl%"=="" (
    SET "__MVNW_ERROR__=Cannot read distributionUrl property in %~dp0\.mvn\wrapper\maven-wrapper.properties"
    GOTO err
)
@SET "MVNW_distributionUrlName=%MVNW_distributionUrl:/=\%"
@SET "MVNW_distributionUrlName=%MVNW_distributionUrlName:*/=%"
@SET "MVNW_distributionUrlNameMain=%MVNW_distributionUrlName:.zip=%"
@SET "MVNW_distributionUrlNameMain=%MVNW_distributionUrlNameMain:-bin=%"
@SET "MVNW_HASH=0"
@IF NOT "%MVNW_VERBOSE%"=="true" SET MVNW_QUIET=1
@SET "MVNW_USER_HOME=%USERPROFILE%\.m2\wrapper\dists"
@SET "MVNW_DIST_DIR=%MVNW_USER_HOME%\%MVNW_distributionUrlNameMain%"
@SET "MVNW_DIST_HASH=%MVNW_DIST_DIR%"

@REM Determine the Maven command to use
@SET MVN_CMD=mvn
@IF "%MVNW_distributionUrl:mvnd=%" NEQ "%MVNW_distributionUrl%" SET MVN_CMD=mvnd

@IF EXIST "%MVNW_DIST_DIR%" (
    @ECHO Found existing Maven distribution at %MVNW_DIST_DIR% 1>&2
    @GOTO exec
)

@REM Download Maven distribution
@ECHO Downloading from: %MVNW_distributionUrl% 1>&2

@IF NOT EXIST "%MVNW_USER_HOME%" MKDIR "%MVNW_USER_HOME%"

@SET "MVNW_DIST_DIR=%MVNW_USER_HOME%\%MVNW_distributionUrlNameMain%"
@IF NOT EXIST "%MVNW_DIST_DIR%" MKDIR "%MVNW_DIST_DIR%"

@SET "MVNW_ZIP_FILE=%MVNW_DIST_DIR%\%MVNW_distributionUrlName%"

@REM Try to download using PowerShell
@WHERE powershell >NUL 2>&1
@IF %ERRORLEVEL% EQU 0 (
    @ECHO Downloading with PowerShell... 1>&2
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $ProgressPreference = 'SilentlyContinue'; Invoke-WebRequest -Uri '%MVNW_distributionUrl%' -OutFile '%MVNW_ZIP_FILE%'}"
    @IF %ERRORLEVEL% NEQ 0 (
        SET "__MVNW_ERROR__=Failed to download Maven distribution using PowerShell"
        GOTO err
    )
) ELSE (
    @REM Try to download using certutil
    @WHERE certutil >NUL 2>&1
    @IF %ERRORLEVEL% EQU 0 (
        @ECHO Downloading with certutil... 1>&2
        certutil -urlcache -split -f "%MVNW_distributionUrl%" "%MVNW_ZIP_FILE%"
        @IF %ERRORLEVEL% NEQ 0 (
            SET "__MVNW_ERROR__=Failed to download Maven distribution using certutil"
            GOTO err
        )
    ) ELSE (
        SET "__MVNW_ERROR__=No download tool found. Please install PowerShell or certutil."
        GOTO err
    )
)

@REM Unzip
@ECHO Unpacking %MVNW_ZIP_FILE% to %MVNW_DIST_DIR% 1>&2
@WHERE tar >NUL 2>&1
@IF %ERRORLEVEL% EQU 0 (
    tar -xf "%MVNW_ZIP_FILE%" -C "%MVNW_DIST_DIR%"
) ELSE (
    @WHERE powershell >NUL 2>&1
    @IF %ERRORLEVEL% EQU 0 (
        powershell -Command "& {Expand-Archive -Path '%MVNW_ZIP_FILE%' -DestinationPath '%MVNW_DIST_DIR%'}"
    ) ELSE (
        SET "__MVNW_ERROR__=No unzip tool found. Please install tar or PowerShell."
        GOTO err
    )
)

@REM Clean up zip
@DEL /Q "%MVNW_ZIP_FILE%" 2>NUL

:exec
@SET "MAVEN_HOME=%MVNW_DIST_DIR%"
@FOR /D %%I IN ("%MAVEN_HOME%\apache-maven-*") DO @SET "MAVEN_HOME=%%I"
@FOR /D %%I IN ("%MAVEN_HOME%\maven-mvnd-*") DO @SET "MAVEN_HOME=%%I"

@IF NOT EXIST "%MAVEN_HOME%\bin\%MVN_CMD%.cmd" (
    SET "__MVNW_ERROR__=Cannot find %MVN_CMD% in %MAVEN_HOME%\bin\"
    GOTO err
)

@ECHO Executing %MAVEN_HOME%\bin\%MVN_CMD%.cmd %* 1>&2
@"%MAVEN_HOME%\bin\%MVN_CMD%.cmd" %*
@IF %ERRORLEVEL% NEQ 0 goto err
@goto end

:err
@ECHO %__MVNW_ERROR__% 1>&2
@EXIT /B 1

:end
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%

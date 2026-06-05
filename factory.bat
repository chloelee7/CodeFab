@echo off
@rem
@rem CodeFab factory control shell launcher (Windows).
@rem
@rem   factory                 Prompt mode (interactive REPL)
@rem   factory run <file>      File mode (run a script once)
@rem   factory debug <file>    Debug mode (interactive debugger)
@rem   factory --help          Usage
@rem
@rem Args are passed straight through to codefab.shell.Main, which dispatches the
@rem mode. Compiled classes are built on first run (or when missing).
setlocal

set "DIR=%~dp0"
set "CLASSES=%DIR%build\classes\java\main"

if not exist "%CLASSES%\codefab\shell\Main.class" (
    echo Building CodeFab... 1>&2
    call "%DIR%gradlew.bat" -q classes
    if errorlevel 1 exit /b 1
)

java -cp "%CLASSES%" codefab.shell.Main %*
exit /b %ERRORLEVEL%

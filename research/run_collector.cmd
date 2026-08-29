@echo off
rem CoinWin research collector - keeps collect.py alive.
rem Why a loop: collect.py already survives a failed slot, but not process death
rem (network stack reset, python crash). Windows Task Scheduler starts this at logon;
rem this loop is what brings the collector back if the process itself dies.
rem Order book cannot be backfilled - every minute down is lost forever.
cd /d "%~dp0"
:loop
python collect.py >> data\collect.log 2>&1
echo [%date% %time%] collector exited, restarting in 10s >> data\collect.log
timeout /t 10 /nobreak > nul
goto loop

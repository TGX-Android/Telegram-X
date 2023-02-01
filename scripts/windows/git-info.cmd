@ECHO OFF
for /f "delims=" %%x in ('git rev-parse --short HEAD') do set tgx_shorthash=%%x
for /f "delims=" %%x in ('git rev-parse HEAD') do set tgx_hash=%%x
for /f "delims=" %%x in ('"git show -s --format=%%ct"') do set tgx_date=%%x
for /f "delims=" %%x in ('git config --get remote.origin.url') do set tgx_remote_url=%%x
for /f "delims=" %%x in ('"git log -1 --pretty=format:%%an"') do set tgx_author=%%x
ECHO %tgx_shorthash% %tgx_hash% %tgx_date% %tgx_remote_url% %tgx_author%
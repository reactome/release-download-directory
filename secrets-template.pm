package GKB::Secrets;
# Not be commited on git hub (see .gitignore)

use strict;
use vars qw/
$GK_DB_HOST
$GK_DB_USER
$GK_DB_PASS
$GK_DB_NAME
$GK_IDB_NAME
$GK_DB_PORT
/;

$GK_DB_HOST  = 'localhost';
$GK_DB_USER  = '${DB_USER}';
$GK_DB_PASS  = '${DB_PASS}';
$GK_DB_NAME  = 'current';
$GK_IDB_NAME = 'stable_identifiers';
$GK_DB_PORT  = 3306;

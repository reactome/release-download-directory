#!/usr/bin/perl
use strict;
use warnings;

use CGI;

use GKB::DBAdaptor;
use GKB::WebUtils;

my $cgi = CGI->new();

my $user = $cgi->param("db_user");
my $pass = $cgi->param("db_pass");
my $host = $cgi->param("db_host");
my $db = $cgi->param("DB");

my $dba = GKB::DBAdaptor->new(
    -user   => $user,
    -pass   => $pass,
    -host   => $host,
    -dbName => $db
);

my $wu = GKB::WebUtils->new(-cgi => $cgi, -dba => $dba);
my $id = $cgi->param("id") || die "No ID specified specified.\n";
my $file_name = $cgi->param("file_name") || die "No filename specified specified.\n";

$wu->create_protege_project_wo_orthologues($file_name,[$id]);
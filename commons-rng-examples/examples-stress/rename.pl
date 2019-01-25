#!/usr/bin/perl -w

use Getopt::Long;
use File::Basename;
#require 'common.pl';

my $prog = basename($0);
my $tmpFile = "/tmp/$prog.$$";
my $out = "$prog.out";
my $debug = 0;

my $usage = "
  Program to rename output from the DieHarder and BigCrush analysis

Usage:

  $prog input [...]

Options:

  input     ...

  -help     Print this help and exit

";

my $help;
GetOptions(
	"help" => \$help,
);

die $usage if $help;

@ARGV or die $usage;

my %map = (
  "org.apache.commons.rng.core.source32.JDKRandom" => "1",
  "org.apache.commons.rng.core.source32.MersenneTwister" => "2",
  "org.apache.commons.rng.core.source32.Well512a" => "3",
  "org.apache.commons.rng.core.source32.Well1024a" => "4",
  "org.apache.commons.rng.core.source32.Well19937a" => "5",
  "org.apache.commons.rng.core.source32.Well19937c" => "6",
  "org.apache.commons.rng.core.source32.Well44497a" => "7",
  "org.apache.commons.rng.core.source32.Well44497b" => "8",
  "org.apache.commons.rng.core.source32.ISAACRandom" => "9",
  "org.apache.commons.rng.core.source64.MersenneTwister64" => "10",
  "org.apache.commons.rng.core.source64.SplitMix64" => "11",
  "org.apache.commons.rng.core.source64.XorShift1024Star" => "12",
  "org.apache.commons.rng.core.source64.TwoCmres" => "13",
  "org.apache.commons.rng.core.source32.MultiplyWithCarry256" => "14",
  "org.apache.commons.rng.core.source32.KISSRandom" => "15",
);

my @files;
for (@ARGV) {
	if (m/\*/) {
		push @files, glob "$_";
	} else {
		push @files, $_;
	}
}

for $input (@files)
{
	open (IN, $input) or die "Failed to open '$input': $!\n";
	$id = 0;
	$rng = '';
	while (<IN>)
	{
        if (m/^# RNG: (\S+)/) {
            $rng = $1;
            $id = $map{$1};
        }
	}
	close IN;
	die "No ID for $input\n" unless $id;
	$name = $input;
	$name =~ s/bc/tu/;
	$name =~ s/\d+$/$id/;
        $name = basename($name);
	$dir{$name}++;
	$dir = $dir{$name};
	$name = "run_$dir/" . $name;
	print "$input => $name  $rng\n";
	`cp $input $name`;
}


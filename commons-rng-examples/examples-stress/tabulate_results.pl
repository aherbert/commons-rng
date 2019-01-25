#!/usr/bin/perl -w

use Getopt::Long;
use File::Basename;
require 'common.pl';

my $prog = basename($0);
my $tmpFile = "/tmp/$prog.$$";
my $out = "$prog.out";
my $debug = 0;
my $report_time = 0;
my $limit = 100;

my $usage = "
  Program to tabulate the results from DieHarder and TestU01 (BigCrush)

Usage:

  $prog input [...]

Options:

  input     Results files

  -help     Print this help and exit

  -time     Print the mean time table

  -limit=%n Limit for systematic failure ($limit)

";

my $help;
GetOptions(
	"help" => \$help,
	"time" => \$report_time,
	"limit=n" => \$limit,
);

die $usage if $help;

@ARGV or die $usage;

# Map to the enum name of the RNG
my %map = (
  "org.apache.commons.rng.core.source32.JDKRandom" => "JDK",
  "org.apache.commons.rng.core.source32.MersenneTwister" => "MT",
  "org.apache.commons.rng.core.source32.Well512a" => "WELL_512_A",
  "org.apache.commons.rng.core.source32.Well1024a" => "WELL_1024_A",
  "org.apache.commons.rng.core.source32.Well19937a" => "WELL_19937_A",
  "org.apache.commons.rng.core.source32.Well19937c" => "WELL_19937_C",
  "org.apache.commons.rng.core.source32.Well44497a" => "WELL_44497_A",
  "org.apache.commons.rng.core.source32.Well44497b" => "WELL_44497_B",
  "org.apache.commons.rng.core.source32.ISAACRandom" => "ISAAC",
  "org.apache.commons.rng.core.source64.MersenneTwister64" => "MT_64",
  "org.apache.commons.rng.core.source64.SplitMix64" => "SPLIT_MIX_64",
  "org.apache.commons.rng.core.source64.XorShift1024Star" => "XOR_SHIFT_1024_S",
  "org.apache.commons.rng.core.source64.TwoCmres" => "TWO_CMRES",
  "org.apache.commons.rng.core.source32.MultiplyWithCarry256" => "MWC_256",
  "org.apache.commons.rng.core.source32.KISSRandom" => "KISS",
);

# The output order for reporting
my %order = (
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

# Test order for Dieharder
my %dhorder;

for $input (@files)
{
	open (IN, $input) or die "Failed to open '$input': $!\n";
	chomp(@data = <IN>);
	close IN;
	
	$test = '';
	for (@data) {
        if (m/^# RNG: (\S+)/) {
            $class = $1;
        }
        elsif (m/^#            dieharder/) {
            $test = 'dh';
            last;
        } elsif (m/^                 Starting BigCrush/) {
            $test = 'bc';
            last;
        }
	}
	
	die "Unknown test suite for $input\n" unless $test;
	die "Unknown RNG for $input\n" unless $map{$class};
	
    $fail = 0;
    $suite = '';
    $time = 0;
        # For systematic failures look only at the test number
        my %local;

	if ($test eq 'dh') {
        $suite = "Dieharder";
	# Store test order
	if (!keys %dhorder) {
		$i=0;
		for (@data) {
			next if m/^#/;
			@tmp = (split /\|/, $_);
			if (@tmp == 6) {
			$test = "$tmp[0]:$tmp[1]";
			$test =~ s/ //g;
			$dhorder{$test} = $i++;
			}
		}
	}
        # Just count the number of failed tests
        for (@data) {
            if (m/FAILED/) {
                # Get the test to look for systematic failures
                # The test has name then parameter:

                #      rgb_lagged_sum|  23|   1000000|     100|0.00222016|   WEAK   
                #      rgb_lagged_sum|  23|   1000000|     200|0.00000049|  FAILED  
                #      rgb_lagged_sum|  24|   1000000|     100|0.29465078|  PASSED 
                #
                # Parse as:
                # rgb_lagged_sum:23

		@tmp = (split /\|/, $_);
		$test = "$tmp[0]:$tmp[1]";
		$test =~ s/ //g;
                # Ignore 'Sums Test'
                next if m/diehard_sums/;
                $local{$test} = 1;
                $fail++;
            } elsif (m/^# Test duration: (\S+)/) {
                $time = $1;
            }
        }
	} elsif ($test eq 'bc') {
        $suite = "TestU01 (BigCrush)";
        $summary = 0;

        for (@data) {
            # Look for the summary
            if (m/========= Summary results of BigCrush =========/) {
                $summary = 1;
            } elsif ($summary && m/^ *([0-9]+) +(\w+)/) {
                # Get the test to look for systematic failures
                # The test has a number and then a title using letters,
                # Optional sub parts of the test follow:

                # 25  ClosePairs mNP2, t = 16          eps  
                # 25  ClosePairs NJumps, t = 16        eps  
                # 25  ClosePairs mNP2S, t = 16         eps  
                # 26  SimpPoker, r = 0                 eps  
                # 28  SimpPoker, r = 0                 eps  
                #
                # Parse as:
                # 25:ClosePairs
                # 26:SimpPoker
                # 28:SimpPoker
             
		$test = "$1:$2";
                $local{$test} = 1;
                #print "$input  $map{$class}  $_\n";
                $fail++;
            } elsif ($summary && m/^# Test duration: (\S+)/) {
                $time = $1;
            }
        }
	} else {
        die "Unknown test $test for $input\n";
	}

	# Acrue failures on entire tests.
        # Note: For TestU01 BigCrush the fail count include sub-parts of the test.
	for $test (keys %local) {
                $fail{$class}{$suite}{$test}++;
	}
	$count{$class}{$suite}++;
	
	push @{ $data{$class}{$suite} }, $fail;
	push @{ $time{$class}{$suite} }, $time;
	#print "$input  $map{$class}  $suite  $fail\n";
}

@suites = ( 'Dieharder', 'TestU01 (BigCrush)' );

print "|| RNG identifier || Dieharder   || TestU01 (BigCrush) ||\n";
for $class (sort { $order{$a} <=> $order{$b} } keys %data) {
    $rng = $map{$class};
    print "| $rng | "; 
    for $suite (@suites) {
        @value = (defined $data{$class}{$suite}) ? @{ $data{$class}{$suite} } : ();
        print "@value |";
    }
    print "\n";
}

if ($report_time) {
print "\n";
print "|| RNG identifier || Dieharder   || TestU01 (BigCrush) ||\n";
for $class (sort { $order{$a} <=> $order{$b} } keys %time) {
    $rng = $map{$class};
    print "| $rng | "; 
    for $suite (@suites) {
        @value = (defined $time{$class}{$suite}) ? @{ $time{$class}{$suite} } : ();
        ($av,$sd) = av_sd(\@value);
        printf "%.2f +/- %.2f |", $av, $sd;
    }
    print "\n";
}
}

# Systematic failures
$limit /= 100.0;
print "\n";
print "|| RNG identifier || Test suite || Systematic failures || Fails ||\n";
for $class (sort { $order{$a} <=> $order{$b} } keys %data) {
    $rng = $map{$class};
    for $suite (@suites) {
	$total = $count{$class}{$suite};
	for $test (sort {
                        # BigCrush is sorted by test Id
			if ($a =~ m/^(\d+)/) {
				$id = $1;
				$b =~ m/^(\d+)/;
				return $id <=> $1;
			} else { 
				# Dieharder by the order in the result file
				return $dhorder{$a} <=> $dhorder{$b};
			}
			} keys %{$fail{$class}{$suite}}) {
            $fails = $fail{$class}{$suite}{$test};
            print "| $rng | $suite | $test | $fails/$total |\n" if $fails>=$total * $limit;
        }
    }
}

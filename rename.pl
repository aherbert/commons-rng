#!/usr/bin/perl -w

@randomSource = (
    JDK,
    WELL_512_A,
    WELL_1024_A,
    WELL_19937_A,
    WELL_19937_C,
    WELL_44497_A,
    WELL_44497_B,
    MT,
    ISAAC,
    SPLIT_MIX_64,
    XOR_SHIFT_1024_S,
    TWO_CMRES,
    MT_64,
    MWC_256,
    KISS);

@generatorsList = (
    JDK,
    MT,
    WELL_512_A,
    WELL_1024_A,
    WELL_19937_A,
    WELL_19937_C,
    WELL_44497_A,
    WELL_44497_B,
    ISAAC,
    MT_64,
    SPLIT_MIX_64,
    XOR_SHIFT_1024_S,
    TWO_CMRES,
    MWC_256,
    KISS);

@az = qw(A B C D E F G H I J K L M N O P Q R S T U V W X Y Z);

scalar(@generatorsList) == scalar(@randomSource) or
  die "List length mismatch";

sub indexOf($) {
  my $rng = shift;
  for ($j=0; $j<=$#randomSource; $j++) {
    if ($rng eq $randomSource[$j]) {
      return $j+1;
    }
  }
  die "Cannot find $rng\n";
}

my @perl;
my @step1;
my @step2;

$i = 0;
for $rng (@generatorsList) {
  $mid = $az[$i];
  $i++;
  $end = indexOf($rng);
  if ($i != $end) {
    push @perl, "if (m#$rng.*{{{../txt/userguide/stress/dh/run_1#) { s/dh_$i/dh_$end/g; s/tu_$i/tu_$end/g; }\n";
    push @step1, "git mv xx_$i xx_$mid\n";
    push @step2, "git mv xx_$mid xx_$end\n";
    printf "%-20s %2d => $mid => %2d\n", $rng, $i, $end;
  }
}

push @perl, "print;\n";

print "> perl -i script src/site/apt/userguide/rng.apt\n";
open (OUT, ">script.pl") or die;
print OUT $_ for @perl;
close OUT;

print "> sh git_mv.sh\n";
open (OUT, ">git_mv.sh") or die;

for $run (1 .. 3) {
  for $test (qw(dh tu)) {
    print OUT "cd src/site/resources/txt/userguide/stress/$test/run_$run\n";
    for (@step1) {
      $tmp = $_;
      $tmp =~ s#xx_#${test}_#g;
      print OUT $tmp;
    }
    for (@step2) {
      $tmp = $_;
      $tmp =~ s#xx_#${test}_#g;
      print OUT $tmp;
    }
    print OUT "cd -\n";
  }
}
close OUT;

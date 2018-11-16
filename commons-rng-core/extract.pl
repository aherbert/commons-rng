#!/usr/bin/perl
while (<>) {
  if (m/^org.apache.commons.rng.core.source\d+.(\S+).*: n = (\d+) <(\S+)> \((\d+) /) {
    if ($2 == 1234) {
      print "$1 nextFloat() $4\n";
    } elsif ($2 == 578) {
      print "$1 nextDouble() $4\n";
    } else {
      print "$1 next$3($2) $4\n";
    }
  }
}

#!/bin/bash
for n in {1..1000}
do
  mvn test -Dtest=ProvidersCommon* >> test_orig.out
done

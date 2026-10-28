package com.argument.segmentationtrial;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class BoundaryAgreementTest {
 private final BoundaryAgreement calc=new BoundaryAgreement();
 @Test void exactPointEightFails(){var r=calc.calculate(List.of(Set.of(1,2),Set.of(1,2,3)));assertThat(r.score()).isEqualTo(0.8);assertThat(r.passed()).isFalse();assertThat(r.numerator()).isEqualTo("4");assertThat(r.denominator()).isEqualTo("5");}
 @Test void aboveThresholdPasses(){assertThat(calc.calculate(List.of(Set.of(1,2,3),Set.of(1,2,3,4))).passed()).isTrue();}
 @Test void noInternalBoundariesAndOneEmptyAreDefined(){assertThat(calc.calculate(List.of(Set.of(),Set.of())).score()).isEqualTo(1);assertThat(calc.calculate(List.of(Set.of(),Set.of(1))).score()).isZero();}
 @Test void nPeopleAverageAllPairsEqually(){var r=calc.calculate(List.of(Set.of(1,2),Set.of(1,2),Set.of(1)));assertThat(r.pairs()).hasSize(3);assertThat(r.score()).isCloseTo(7.0/9.0,within(1e-15));assertThat(r.passed()).isFalse();}
}

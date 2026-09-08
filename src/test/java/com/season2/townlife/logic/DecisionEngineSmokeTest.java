package com.season2.townlife.logic;

import java.util.EnumSet;

public final class DecisionEngineSmokeTest {
    public static void main(String[] args) {
        criticalHungerBeatsWork();
        dangerForcesShelter();
        nighttimeLowEnergyForcesSleep();
        sociableResidentPrefersSocialising();
        shortBasicActivitiesStayStable();
        System.out.println("Town Life decision engine smoke tests passed.");
    }

    private static void criticalHungerBeatsWork() {
        Needs needs = new Needs();
        needs.set(NeedType.HUNGER, 5F);
        Activity chosen = DecisionEngine.choose(new DecisionContext(
                needs,
                EnumSet.of(PersonalityTrait.DILIGENT),
                DailyFocus.WORK,
                false, false, true, false,
                true, true, true, true, true, true));
        assertActivity(Activity.EAT, chosen, "Critical hunger should beat work");
    }

    private static void dangerForcesShelter() {
        Needs needs = new Needs();
        Activity chosen = DecisionEngine.choose(new DecisionContext(
                needs,
                EnumSet.noneOf(PersonalityTrait.class),
                DailyFocus.BALANCED,
                true, false, true, false,
                true, true, true, true, true, true));
        assertActivity(Activity.SHELTER, chosen, "Danger should force shelter");
    }

    private static void nighttimeLowEnergyForcesSleep() {
        Needs needs = new Needs();
        needs.set(NeedType.ENERGY, 18F);
        Activity chosen = DecisionEngine.choose(new DecisionContext(
                needs,
                EnumSet.noneOf(PersonalityTrait.class),
                DailyFocus.BALANCED,
                false, false, false, true,
                true, true, true, true, false, true));
        assertActivity(Activity.SLEEP, chosen, "Low energy at night should select sleep");
    }

    private static void sociableResidentPrefersSocialising() {
        Needs needs = new Needs();
        needs.set(NeedType.SOCIAL, 35F);
        needs.set(NeedType.FUN, 70F);
        Activity chosen = DecisionEngine.choose(new DecisionContext(
                needs,
                EnumSet.of(PersonalityTrait.SOCIABLE),
                DailyFocus.SOCIAL,
                false, false, false, false,
                true, true, true, true, false, true));
        assertActivity(Activity.SOCIALISE, chosen, "Sociable resident with low social need should socialise");
    }

    private static void shortBasicActivitiesStayStable() {
        Needs needs = new Needs();
        needs.set(NeedType.HUNGER, 4F);
        if (DecisionEngine.shouldInterrupt(Activity.EAT, needs, false)) {
            throw new AssertionError("Eating should not be interrupted by its own critical hunger state");
        }
        if (!DecisionEngine.shouldInterrupt(Activity.EAT, needs, true)) {
            throw new AssertionError("Danger must still interrupt eating");
        }
    }

    private static void assertActivity(Activity expected, Activity actual, String message) {
        if (actual != expected) {
            throw new AssertionError(message + "; expected " + expected + " but got " + actual);
        }
    }
}

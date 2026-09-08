package com.season2.townlife.logic;

import java.util.EnumMap;
import java.util.Map;

public final class DecisionEngine {
    private DecisionEngine() {}

    public static Activity choose(DecisionContext context) {
        if (context.danger() && context.canShelter()) {
            return Activity.SHELTER;
        }

        EnumMap<Activity, Double> score = new EnumMap<>(Activity.class);
        score.put(Activity.IDLE, 8D);

        float hunger = context.needs().get(NeedType.HUNGER);
        float energy = context.needs().get(NeedType.ENERGY);
        float social = context.needs().get(NeedType.SOCIAL);
        float fun = context.needs().get(NeedType.FUN);

        if (context.canEat()) {
            double eat = (100D - hunger) * 1.28D;
            if (hunger < 25F) eat += 70D;
            if (context.dailyFocus() == DailyFocus.ERRANDS) eat += 10D;
            score.put(Activity.EAT, eat);
        }

        if (context.hasHome()) {
            double sleep = (100D - energy) * 1.20D;
            if (energy < 22F) sleep += 75D;
            if (context.nighttime()) sleep += 45D;
            score.put(Activity.SLEEP, sleep);
        }

        if (context.canSocialise()) {
            double socialise = (100D - social) * 0.98D;
            if (context.traits().contains(PersonalityTrait.SOCIABLE)) socialise += 18D;
            if (context.traits().contains(PersonalityTrait.RESERVED)) socialise -= 16D;
            if (context.dailyFocus() == DailyFocus.SOCIAL) socialise += 18D;
            score.put(Activity.SOCIALISE, socialise);
        }

        if (context.canRelax()) {
            double relax = (100D - fun) * 0.92D;
            if (context.traits().contains(PersonalityTrait.LEISURELY)) relax += 14D;
            if (context.traits().contains(PersonalityTrait.DILIGENT)) relax -= 5D;
            if (context.dailyFocus() == DailyFocus.LEISURE) relax += 18D;
            score.put(Activity.RELAX, relax);
        }

        if (context.workHours() && context.canWork()) {
            double work = 62D;
            if (context.traits().contains(PersonalityTrait.DILIGENT)) work += 20D;
            if (context.traits().contains(PersonalityTrait.LEISURELY)) work -= 10D;
            if (context.dailyFocus() == DailyFocus.WORK) work += 18D;
            if (hunger < 30F || energy < 25F) work -= 55D;
            score.put(Activity.WORK, work);
        }

        if (context.raining() && context.canShelter() && !context.workHours()) {
            score.merge(Activity.SHELTER, 44D, Math::max);
        }

        if (context.nighttime() && context.hasHome()) {
            score.put(Activity.RETURN_HOME, 54D);
        }

        return score.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(Activity.IDLE);
    }

    public static boolean shouldInterrupt(Activity current, Needs needs, boolean danger) {
        if (danger) return current != Activity.SHELTER;
        if (current == Activity.EAT || current == Activity.SLEEP || current == Activity.SHELTER) {
            return false;
        }
        return needs.get(NeedType.HUNGER) < 12F || needs.get(NeedType.ENERGY) < 10F;
    }
}

package com.season2.townlife.logic;

import java.util.EnumSet;

public record DecisionContext(
        Needs needs,
        EnumSet<PersonalityTrait> traits,
        DailyFocus dailyFocus,
        boolean danger,
        boolean raining,
        boolean workHours,
        boolean nighttime,
        boolean hasHome,
        boolean canEat,
        boolean canSocialise,
        boolean canRelax,
        boolean canWork,
        boolean canShelter) {
}

package com.elfmcys.yesstevemodel.client.animation.condition;

public class ArmorConditions {
    private final ConditionArmor conditionArmor = new ConditionArmor();

    public void addCondition(String name) {
        this.conditionArmor.addTest(name);
    }

    public ConditionArmor getConditionArmor() {
        return this.conditionArmor;
    }
}

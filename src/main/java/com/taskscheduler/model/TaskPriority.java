package com.taskscheduler.model;

public enum TaskPriority {
    HIGH(3), MEDIUM(2), LOW(1);
    private final int weight;

    TaskPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
// constructor + getWeight()
}
//The weight field is what your PriorityBlockingQueue will compare on later. This is your first lesson in enums with behavior — Java enums aren't just named constants.

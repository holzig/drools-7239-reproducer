package com.example;

import java.util.Objects;


public class Gate implements IGate {

    private boolean valid;
    private String name;

    public Gate(final boolean valid, final String name) {
        this.valid = valid;
        this.name = name;
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public void setValid(final boolean valid) {
        this.valid = valid;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Gate gate = (Gate) o;
        return valid == gate.valid && Objects.equals(name, gate.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(valid, name);
    }

}

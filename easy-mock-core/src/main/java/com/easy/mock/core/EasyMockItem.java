package com.easy.mock.core;

public class EasyMockItem implements Comparable<EasyMockItem> {

    private String code;

    private String name;

    private String doc;

    private Class<?> type = String.class;

    private boolean required;

    private Integer order = 999;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Integer getOrder() {
        return order;
    }

    public void setOrder(Integer order) {
        this.order = order;
    }

    public EasyMockItem() {
    }

    public EasyMockItem(String name, String doc) {
        this.name = name;
        this.doc = doc;
    }

    public EasyMockItem(String name, String doc, Integer order) {
        this.name = name;
        this.doc = doc;
        this.order = order;
    }

    public EasyMockItem(String name, String doc, Integer order, boolean required) {
        this.name = name;
        this.doc = doc;
        this.required = required;
        this.order = order;
    }

    @Override
    public int compareTo(EasyMockItem other) {
        return order.equals(other.getOrder()) ? name.compareTo(other.getName()) : order.compareTo(other.getOrder());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        EasyMockItem easyMockItem = (EasyMockItem) o;

        return name.equals(easyMockItem.name);

    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "EasyMockItem{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", doc='" + doc + '\'' +
                ", type=" + type +
                ", required=" + required +
                ", order=" + order +
                '}';
    }
}

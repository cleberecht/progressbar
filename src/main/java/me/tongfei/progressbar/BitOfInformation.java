package me.tongfei.progressbar;

import java.util.function.Supplier;

/**
 * @author cl
 */
public class BitOfInformation {

    private String tooltip;
    private String information;

    private Supplier<String> supplier;

    public BitOfInformation(String tooltip) {
        this.tooltip = tooltip;
    }

    public BitOfInformation(String tooltip, Supplier<String> supplier) {
        this.tooltip = tooltip;
        this.supplier = supplier;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    public String getInformation() {
        return information;
    }

    public void setInformation(String information) {
        this.information = information;
    }

    public void bindInformationSupplier(Supplier<String> supplier) {
        this.supplier = supplier;
    }

    public String getBit() {
        if (supplier == null) {
            return tooltip + ": " + information;
        } else {
            return tooltip + ": " + supplier.get();
        }
    }

    public int getLength() {
        // might get problems with the length and asynchronous calls
        return getBit().length();
    }

}

package me.tongfei.progressbar;

/**
 * @author cl
 */
public class BitOfInformation {

    private String tooltip;
    private String information;

    public BitOfInformation(String tooltip) {
        this.tooltip = tooltip;
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

    public String getBit() {
        return tooltip+": "+information;
    }

    public int getLength() {
        return getBit().length();
    }

}

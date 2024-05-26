

public class Sort2 {
    private String itemName;
    private int price;
    private double reviewAverage;
    private int reviewCount;

    public Sort2(String itemName, int price, double reviewAverage, int reviewCount) {
        this.itemName = itemName;
        this.price = price;
        this.reviewAverage = reviewAverage;
        this.reviewCount = reviewCount;
    }

    public String getItemName() {
        return itemName;
    }

    public int getPrice() {
        return price;
    }

    public double getReviewAverage() {
        return reviewAverage;
    }

    public int getReviewCount() {
        return reviewCount;
    }
}
// Auto generated code, do not modify
package nxt.http.callers;

public class DgsPurchaseCall extends CreateTransactionCallBuilder<DgsPurchaseCall> {
    private DgsPurchaseCall() {
        super("dgsPurchase");
    }

    public static DgsPurchaseCall create(int chain) {
        return new DgsPurchaseCall().param("chain", chain);
    }

    public DgsPurchaseCall priceNQT(long priceNQT) {
        return param("priceNQT", priceNQT);
    }

    public DgsPurchaseCall quantity(String quantity) {
        return param("quantity", quantity);
    }

    public DgsPurchaseCall deliveryDeadlineTimestamp(String deliveryDeadlineTimestamp) {
        return param("deliveryDeadlineTimestamp", deliveryDeadlineTimestamp);
    }

    public DgsPurchaseCall goods(String goods) {
        return param("goods", goods);
    }

    public DgsPurchaseCall goods(long goods) {
        return unsignedLongParam("goods", goods);
    }
}

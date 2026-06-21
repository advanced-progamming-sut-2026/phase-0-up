package views.renderers;

import utils.Result;

public class ShopRenderer {
    public void listAllProducts(String out){
        System.out.println(out);
    }
    public void listDailyProducts(String out){
        System.out.println(out);
    }
    public void successOfBuyingAProduct(Result result){
        System.out.println(result.message());
    }
}

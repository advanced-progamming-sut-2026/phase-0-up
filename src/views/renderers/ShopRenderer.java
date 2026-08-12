package views.renderers;

import utils.Result;

// The shop's presentation surface. The Commands build the product listing as text and hand it over;
// what happens to that text is the implementation's business.
public interface ShopRenderer {
    void listAllProducts(String out);

    void listDailyProducts(String out);

    void successOfBuyingAProduct(Result result);
}

package dev.peluci.doceenccoreapi.ingredient;

import dev.peluci.doceenccoreapi.category.Category;
import dev.peluci.doceenccoreapi.category.CategoryRepository;
import dev.peluci.doceenccoreapi.category.CategoryType;
import dev.peluci.doceenccoreapi.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
public class IngredientPriceHistoryRepositoryTest {

    @Autowired
    CategoryRepository categories;

    @Autowired
    IngredientRepository ingredients;

    @Autowired
    IngredientPriceHistoryRepository history;

    @Autowired
    JdbcTemplate jdbc;

    private Category category;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM ingredient_price_history");
        jdbc.update("DELETE FROM ingredient");
        jdbc.update("DELETE FROM category");
        category = categories.save(new Category("Farinhas", CategoryType.INGREDIENT));
    }

    @Test
    void returnsTheTwoLatestPurchasesByPurchaseDateEvenWhenInsertedOutOfOrder() {
        Ingredient flour = newIngredient("Farinha de trigo");
        purchase(flour, "10.00", "2026-10-01");
        purchase(flour, "12.00", "2026-10-20");
        purchase(flour, "11.00", "2026-10-10");

        List<IngredientPriceHistory> result = history.findLatestTwoPerIngredient(List.of(flour.getId()));

        assertThat(result).extracting(h -> h.getPrice().getPackagePrice())
                .containsExactlyInAnyOrder(new BigDecimal("12.00"), new BigDecimal("11.00"));
        assertThat(history.findLatestPurchaseDate(flour.getId())).contains(LocalDate.parse("2026-10-20"));
    }

    @Test
    void breaksTiesOnTheSameDayByRegistrationOrder() {
        Ingredient sugar = newIngredient("Açúcar");
        purchase(sugar, "4.00", "2026-10-01");
        purchase(sugar, "5.00", "2026-10-20");
        purchase(sugar, "6.00", "2026-10-20");

        List<IngredientPriceHistory> result = history.findLatestTwoPerIngredient(List.of(sugar.getId()));

        assertThat(result).extracting(h -> h.getPrice().getPackagePrice())
                .containsExactlyInAnyOrder(new BigDecimal("5.00"), new BigDecimal("6.00"));

    }

    @Test
    void handlesSeveralIngredientsAndSingleEntryHistories() {
        Ingredient flour = newIngredient("Farinha de trigo");
        Ingredient salt = newIngredient("Sal");
        purchase(flour, "10.00", "2026-10-01");
        purchase(flour, "12.00", "2026-10-20");
        purchase(salt, "3.00", "2026-10-05");

        List<IngredientPriceHistory> result = history.findLatestTwoPerIngredient(List.of(flour.getId(), salt.getId()));

        assertThat(result).hasSize(3);
        assertThat(result).filteredOn(h -> h.getIngredientId().equals(salt.getId())).hasSize(1);
    }

    @Test
    void findByIdentityTreatsAMissingBrandAsEmptyAndIgnoreCase() {
        Ingredient plain = newIngredient("Leite condensado");

        assertThat(ingredients.findByIdentity("LEITE CONDENSADO", "")).get()
                .extracting(Ingredient::getId).isEqualTo(plain.getId());
        assertThat(ingredients.findByIdentity("Leite condensado", "Moça")).isEmpty();
    }

    private Ingredient newIngredient(String name) {
        IngredientPrice price = IngredientPrice.of(new BigDecimal("8.90"), BigDecimal.ONE, InputUnit.KG, BaseUnit.GRAM);
        return ingredients.save(new Ingredient(name, null, category, BaseUnit.GRAM, price));
    }

    private void purchase(Ingredient ingredient, String packagePrice, String purchasedOn) {
        IngredientPrice price = IngredientPrice.of(new BigDecimal(packagePrice), BigDecimal.ONE, InputUnit.KG, BaseUnit.GRAM);
        history.saveAndFlush(new IngredientPriceHistory(ingredient, price, LocalDate.parse(purchasedOn)));
    }
}

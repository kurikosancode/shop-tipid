package com.shop;

import com.shop.model.User;
import com.shop.service.AuthService;
import com.shop.service.BundleService;
import com.shop.service.CartService;
import com.shop.service.OrderService;
import com.shop.service.ProductService;
import com.shop.service.RecommendationService;
import com.shop.storage.JsonStore;
import com.shop.ui.AdminMenu;
import com.shop.ui.AuthMenu;
import com.shop.ui.ColorUtil;
import com.shop.ui.CustomerMenu;
import com.shop.ui.HistoryMenu;
import com.shop.ui.InputUtil;
import com.shop.ui.RecommendationMenu;
import com.shop.ui.ScreenUtil;

/**
 * Program entry point and main loop.
 * Builds every service once (loading the JSON files), then loops: login screen -> customer or
 * admin menu -> log out -> login screen, until the user chooses Exit.
 *
 * The loop is wrapped in safe handling: any unexpected exception shows a friendly message
 * and returns to the login screen instead of crashing. If the keyboard input is closed
 * (Ctrl+D / Ctrl+Z) the program exits quietly.
 *
 * Run with --no-color (or set NO_COLOR=1) to turn colors off.
 */
public final class Main {

    private Main() { }

    public static void main(String[] args) {
        ColorUtil.init(args);

        // ---- services (each one loads its data from the JSON files in /data)
        JsonStore store = new JsonStore();
        AuthService auth = new AuthService(store);
        ProductService productService = new ProductService(store);
        CartService cartService = new CartService(productService);
        OrderService orderService = new OrderService(store, productService);
        RecommendationService recommendationService = new RecommendationService(productService, orderService);
        BundleService bundleService = new BundleService(productService);

        // ---- screens
        HistoryMenu historyMenu = new HistoryMenu(orderService);
        RecommendationMenu recommendationMenu = new RecommendationMenu(
                recommendationService, bundleService, cartService, auth, productService);
        CustomerMenu customerMenu = new CustomerMenu(productService, cartService, orderService,
                historyMenu, recommendationMenu);
        AdminMenu adminMenu = new AdminMenu(auth, productService, orderService,
                recommendationService, cartService, historyMenu);
        AuthMenu authMenu = new AuthMenu(auth);

        try {
            boolean running = true;
            while (running) {
                try {
                    ScreenUtil.showWarnings(store.drainWarnings());
                    User user = authMenu.run();
                    if (user == null) {
                        running = false;
                    } else {
                        if (user.isAdmin()) {
                            adminMenu.run(user);
                        } else {
                            customerMenu.run(user);
                        }
                        auth.logout();
                    }
                } catch (InputUtil.InputClosedException e) {
                    throw e;
                } catch (Exception e) {
                    auth.logout();
                    showUnexpectedError(e);
                }
            }
        } catch (InputUtil.InputClosedException e) {
            // Input was closed: just fall through to the goodbye message.
        }

        ScreenUtil.clear();
        ScreenUtil.success("Thank you for visiting. Goodbye!");
    }

    private static void showUnexpectedError(Exception e) {
        ScreenUtil.clear();
        ScreenUtil.banner("SOMETHING WENT WRONG");
        System.out.println();
        String detail = e.getMessage() == null ? "" : ": " + e.getMessage();
        ScreenUtil.error("An unexpected error occurred (" + e.getClass().getSimpleName() + detail + ").");
        ScreenUtil.info("Your data files are safe. You will be returned to the login screen.");
        System.out.println();
        InputUtil.pause();
    }
}
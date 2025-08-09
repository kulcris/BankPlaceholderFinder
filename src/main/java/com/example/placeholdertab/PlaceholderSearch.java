package com.example.placeholdertab;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.api.ItemComposition;

class PlaceholderSearch
{
    private static final String QUERY = "is:placeholder";

    // Numeric widget IDs (common across many builds). If your build differs, tell me and I'll map them.
    private static final int BANK_SEARCH_BUTTON_BACKGROUND = 786444;
    private static final int BANK_SEARCH_INPUT = 786445;

    private final Client client;
    private final ItemManager itemManager;

    @Inject
    PlaceholderSearch(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    boolean itemIsPlaceholder(int itemId)
    {
        final ItemComposition comp = itemManager.getItemComposition(itemId);
        return comp != null && (comp.getPlaceholderTemplateId() != -1 || comp.getPlaceholderId() != -1);
    }

    String predicateToken()
    {
        return QUERY;
    }

    /** Returns true if the search box existed and we applied the query. */
    boolean applySearchSafe()
    {
        try
        {
            final Widget searchBox = client.getWidget(BANK_SEARCH_INPUT);
            if (searchBox == null)
            {
                return false; // bank not open / different layout
            }
            searchBox.setText(QUERY);
            safeRunScript(917, 1);            // ensure search active (best effort)
            safeRunScript(915, 1, 1, QUERY);  // apply filter
            return true;
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    void clearSearchIfOurs()
    {
        try
        {
            final Widget searchBox = client.getWidget(BANK_SEARCH_INPUT);
            if (searchBox == null)
            {
                return;
            }
            searchBox.setText("");
            safeRunScript(915, 1, 1, "");
        }
        catch (Throwable ignored) {}
    }

    private void safeRunScript(int id, Object... args)
    {
        try { client.runScript(id, args); } catch (Throwable ignored) {}
    }
}

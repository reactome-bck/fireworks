package org.reactome.web.fireworks.search;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import org.reactome.web.fireworks.controls.common.IconButton;
import org.reactome.web.fireworks.controls.common.IconToggleButton;
import org.reactome.web.fireworks.controls.common.PwpButton;
import org.reactome.web.fireworks.events.SearchKeyPressedEvent;
import org.reactome.web.fireworks.events.SearchResetEvent;
import org.reactome.web.fireworks.handlers.SearchKeyPressedHandler;
import org.reactome.web.fireworks.model.Graph;
import org.reactome.web.fireworks.search.events.*;
import org.reactome.web.fireworks.search.facets.FacetsPanel;
import org.reactome.web.fireworks.search.handlers.*;
import org.reactome.web.fireworks.search.searchbox.*;
import org.reactome.web.fireworks.util.Console;

import java.util.Date;

/**
 * @author Kostas Sidiropoulos <ksidiro@ebi.ac.uk>
 */
public class SearchLauncher extends AbsolutePanel implements ClickHandler, SearchBoxUpdatedHandler,
        SearchBoxArrowKeysHandler, SearchKeyPressedHandler,
        FacetsLoadedHandler, FacetsChangedHandler,
        AutoCompleteSelectedHandler {

    @SuppressWarnings("FieldCanBeLocal")
    private static String OPENING_TEXT = "Search for a term, e.g. pten ...";
    private static int FOCUS_IN_TEXTBOX_DELAY = 300;
    private static String SEARCH_EXPANDED_COOKIE = "pwp-search-expanded-by-default";
    private static int SEARCH_QUERY_MINIMUM_LENGTH = 2;

    private EventBus eventBus;
//    private Context context;
    private Graph graph;

    private SearchBox input = null;
    private PwpButton searchBtn = null;
    private IconButton clearBtn;
    private IconButton executeBtn;
    private IconToggleButton optionsBtn;

    private FlowPanel filtersPanel;
    private FacetsPanel facetsPanel;

    private Boolean isExpanded = false;
    private Boolean isExpandedVertically = false;

    private Timer focusTimer;

    public SearchLauncher(EventBus eventBus, Graph graph) {
        //Setting the search style
        setStyleName(RESOURCES.getCSS().launchPanel());

        this.eventBus = eventBus;
        this.graph = graph;

        this.searchBtn = new PwpButton("Search Reactome", RESOURCES.getCSS().launch(), this);
        this.add(searchBtn);

        this.input = new SearchBox();
        this.input.setStyleName(RESOURCES.getCSS().input());
        this.input.getElement().setPropertyString("placeholder", OPENING_TEXT);
        this.input.getElement().setPropertyBoolean("spellcheck", false);
        this.add(input);

        clearBtn = new IconButton("", RESOURCES.clear());
        clearBtn.setStyleName(RESOURCES.getCSS().clearBtn());
        clearBtn.setVisible(false);
        clearBtn.setTitle("Clear search");
        clearBtn.addClickHandler(event -> clearSearch());
        this.add(clearBtn);

        executeBtn = new IconButton("", RESOURCES.searchGo());
        executeBtn.setStyleName(RESOURCES.getCSS().executeBtn());
        executeBtn.setVisible(true);
        executeBtn.setTitle("Search");
        executeBtn.addClickHandler(this);
//        executeBtn.setEnabled(false);
        this.add(executeBtn);

        optionsBtn = new IconToggleButton("", RESOURCES.options(), RESOURCES.optionsClose(),this);
        optionsBtn.setStyleName(RESOURCES.getCSS().optionsBtn());
        optionsBtn.setVisible(true);
        optionsBtn.setTitle("Filter your results");
        optionsBtn.setEnabled(false);
        this.add(optionsBtn);

        facetsPanel = new FacetsPanel();
        filtersPanel = new FlowPanel();
        filtersPanel.add(facetsPanel);
        this.add(filtersPanel);

        focusTimer = new Timer() {
            @Override
            public void run() {
                SearchLauncher.this.input.setFocus(true);
            }
        };

        this.initHandlers();
        this.searchBtn.setEnabled(true);

        // Expand the panel by default unless the cookie is present
        String expandSearch = Cookies.getCookie(SEARCH_EXPANDED_COOKIE);
        if (expandSearch == null || expandSearch.isEmpty()) {
            if(!isExpanded) {
                this.expandPanel();
            }
        }

    }

    public HandlerRegistration addSearchBoxArrowKeysHandler(SearchBoxArrowKeysHandler handler){
        return input.addSearchBoxArrowKeysHandler(handler);
    }

    public HandlerRegistration addPanelCollapsedHandler(PanelCollapsedHandler handler){
        return addHandler(handler, PanelCollapsedEvent.TYPE);
    }

    public HandlerRegistration addPanelExpandedHandler(PanelExpandedHandler handler){
        return addHandler(handler, PanelExpandedEvent.TYPE);
    }

    public HandlerRegistration addOptionsCollapsedHandler(OptionsCollapsedHandler handler){
        return addHandler(handler, OptionsCollapsedEvent.TYPE);
    }

    public HandlerRegistration addOptionsExpandedHandler(OptionsExpandedHandler handler){
        return addHandler(handler, OptionsExpandedEvent.TYPE);
    }

    public HandlerRegistration addAutoCompleteRequestedHandler(AutoCompleteRequestedHandler handler){
        return addHandler(handler, AutoCompleteRequestedEvent.TYPE);
    }

    public HandlerRegistration addSearchPerformedHandler(SearchPerformedHandler handler){
        return addHandler(handler, SearchPerformedEvent.TYPE);
    }

    @Override
    public void onAutoCompleteSelected(AutoCompleteSelectedEvent event) {
        input.setValue(event.getTerm());
        performSearch();
    }

    @Override
    public void onArrowKeysPressed(SearchBoxArrowKeysEvent event) {
        Integer keyPressed = event.getValue();
        if(keyPressed == KeyCodes.KEY_ESCAPE) {
            setFocus(false);
            this.collapsePanel();
            clearSearch();
        } else if (keyPressed == KeyCodes.KEY_ENTER || keyPressed == KeyCodes.KEY_MAC_ENTER) {
            performSearch();
        }
    }

    @Override
    public void onClick(ClickEvent event) {
        if (event.getSource().equals(this.searchBtn)) {
            if (!isExpanded) {
                expandPanel();
            } else {
                collapsePanel();
                stopExpandingByDefault();
            }
        } else if (event.getSource().equals(this.optionsBtn)) {
            if (!isExpandedVertically) {
                expandPanelVertically();
            } else {
                collapsePanelVertically();
            }
        } else if (event.getSource().equals(this.executeBtn)) {
            performSearch();
        }
    }

    @Override
    public void onFacetsLoaded(FacetsLoadedEvent event) {
        facetsPanel.setFacets(event.getFacets(), event.getSelectedFacets(), event.getScope());
        optionsBtn.setEnabled(!event.getFacets().isEmpty());
    }


    @Override
    public void onSelectedFacetsChanged(FacetsChangedEvent event) {
        performSearch();
    }

    @Override
    public void onSearchBoxUpdated(SearchBoxUpdatedEvent event) {
        getAutoCompleteSuggestions();
        if(input.getText().isEmpty()) {
            optionsBtn.setEnabled(false);
        }
    }

    @Override
    public void onSearchKeyPressed(SearchKeyPressedEvent event) {
        // This is to handle the shortcut [ctrl] + [F]
        // Expand only if search is enabled
        if(searchBtn.isEnabled()) {
            if (!isExpanded) {
                expandPanel();
            } else {
                collapsePanel();
                stopExpandingByDefault();
            }
        }
    }

    public void setFocus(boolean focused){
        this.input.setFocus(focused);
    }

    private void clearSearch() {
        if (!input.getValue().isEmpty()) {
            input.setValue("");
            setFocus(true);
            optionsBtn.setEnabled(false);
        }
        eventBus.fireEventFromSource(new SearchResetEvent(), this);
    }

    private void collapsePanel(){
        if(focusTimer.isRunning()){
            focusTimer.cancel();
        }
        removeStyleName(RESOURCES.getCSS().launchPanelExpanded());
        collapsePanelVertically();
        optionsBtn.setActive(false);
        input.removeStyleName(RESOURCES.getCSS().inputActive());
        isExpanded = false;
        fireEvent(new PanelCollapsedEvent());
    }

    private void collapsePanelVertically() {
        removeStyleName(RESOURCES.getCSS().launchPanelExpandedVertically());
        isExpandedVertically = false;
        fireEvent(new OptionsCollapsedEvent());
    }

    private void expandPanel(){
        addStyleName(RESOURCES.getCSS().launchPanelExpanded());
        input.addStyleName(RESOURCES.getCSS().inputActive());
        isExpanded = true;
        fireEvent(new PanelExpandedEvent());
        focusTimer.schedule(FOCUS_IN_TEXTBOX_DELAY);
    }

    private void expandPanelVertically(){
        addStyleName(RESOURCES.getCSS().launchPanelExpandedVertically());
        isExpandedVertically = true;
        fireEvent(new OptionsExpandedEvent());
    }


    private void initHandlers(){
        this.input.addSearchBoxUpdatedHandler(this);
        this.input.addSearchBoxArrowKeysHandler(this);

        this.facetsPanel.addFacetsChangedHandler(this);

        eventBus.addHandler(SearchKeyPressedEvent.TYPE, this);
    }

    private void getAutoCompleteSuggestions() {
        facetsPanel.clearView();
        collapsePanelVertically();
        optionsBtn.setActive(false);


        String term = input.getText().trim();
        fireEvent(new AutoCompleteRequestedEvent(term));

        showHideClearBtn();
    }

    private void performSearch() {
        String query = input.getText().trim();

        if (query.length() < SEARCH_QUERY_MINIMUM_LENGTH) return;

        SearchArguments searchArgs = new SearchArguments(
                query,
                graph.getSpeciesName(),
                facetsPanel.getSelectedFacets(),
                facetsPanel.getScope()
        );

        if(searchArgs.hasValidQuery()) {
            fireEvent(new SearchPerformedEvent(searchArgs));
            showHideClearBtn();
        }
    }

    private void showHideClearBtn() {
        clearBtn.setVisible(!input.getText().isEmpty());
    }


    private void stopExpandingByDefault() {
        Date expires = new Date();
        Long nowLong = expires.getTime();
        nowLong = nowLong + (1000 * 60 * 60 * 24 * 365L); //One year time
        expires.setTime(nowLong);
        Cookies.setCookie(SEARCH_EXPANDED_COOKIE, "false", expires);
    }

    public static Resources RESOURCES;
    static {
        RESOURCES = GWT.create(Resources.class);
        RESOURCES.getCSS().ensureInjected();
    }

    /**
     * A ClientBundle of resources used by this widget.
     */
    public interface Resources extends ClientBundle {
        /**
         * The styles used in this widget.
         */
        @Source(SearchLauncherCSS.CSS)
        SearchLauncherCSS getCSS();

        @Source("images/search_clicked.png")
        ImageResource launchClicked();

        @Source("images/search_disabled.png")
        ImageResource launchDisabled();

        @Source("images/search_hovered.png")
        ImageResource launchHovered();

        @Source("images/search_normal.png")
        ImageResource launchNormal();

        @Source("images/search_go.png")
        ImageResource searchGo();

        @Source("images/cancel.png")
        ImageResource clear();

        @Source("images/search_options.png")
        ImageResource options();

        @Source("images/search_options_close.png")
        ImageResource optionsClose();

        @Source("images/you_are_here.png")
        ImageResource youAreHere();

        @Source("images/Interactor.png")
        ImageResource interactor();
    }

    /**
     * Styles used by this widget.
     */
    @CssResource.ImportedWithPrefix("fireworks-SearchLauncher")
    public interface SearchLauncherCSS extends CssResource {
        /**
         * The path to the default CSS styles used by this resource.
         */
        String CSS = "org/reactome/web/fireworks/search/SearchLauncher.css";

        String launchPanel();

        String launchPanelExpanded();

        String launchPanelExpandedVertically();

        String launch();

        String input();

        String inputActive();

        String clearBtn();

        String executeBtn();

        String optionsBtn();

        String youAreHereIcon();

    }

}

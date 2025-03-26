package com.chatapp.client.controller;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;

/**
 * Custom ListCell that displays a CheckBox for each string item (e.g., an email).
 * Clicking the CheckBox toggles the selection in the ListView.
 */
public class CheckBoxListCell extends ListCell<String> {
    private final CheckBox checkBox = new CheckBox();

    public CheckBoxListCell() {
        // Whenever the CheckBox is toggled, update the ListView's selection model
        checkBox.setOnAction(evt -> {
            int index = getIndex();
            if (index >= 0 && index < getListView().getItems().size()) {
                if (checkBox.isSelected()) {
                    getListView().getSelectionModel().select(index);
                } else {
                    getListView().getSelectionModel().clearSelection(index);
                }
            }
        });
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            // Use the item's string as the CheckBox text
            checkBox.setText(item);

            // Sync the CheckBox's state with the selection model
            boolean rowSelected = getListView().getSelectionModel().isSelected(getIndex());
            checkBox.setSelected(rowSelected);

            setText(null);
            setGraphic(checkBox);
        }
    }
}

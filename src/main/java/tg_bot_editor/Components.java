package tg_bot_editor;

import megalodonte.props.InputProps;
import megalodonte.base.state.State;
import megalodonte.components.inputs.Input;
import megalodonte.components.inputs.TextAreaInput;

/** Reusable component configuration for the editor. */
final class Components {
    private Components() { }

    static Input input(State<String> value, String placeholder) {
        return new Input(value, field(placeholder));
    }

    static TextAreaInput environment(State<String> value) {
        return new TextAreaInput(value, field("NOME=valor\nOUTRA=valor").height(135).width(300));
    }

    private static InputProps field(String placeholder) {
        return new InputProps().placeHolder(placeholder);
    }
}

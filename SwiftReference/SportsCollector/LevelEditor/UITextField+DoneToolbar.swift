import UIKit

extension UITextField {
    /// `.numberPad` has no Return key, so there's otherwise no way to dismiss
    /// it - the simulator can mask this if a hardware keyboard is attached,
    /// but a real device has none.
    func addDoneButtonToolbar() {
        let toolbar = UIToolbar()
        toolbar.sizeToFit()
        let done = UIBarButtonItem(
            systemItem: .done, primaryAction: UIAction { [weak self] _ in self?.resignFirstResponder() }
        )
        toolbar.items = [UIBarButtonItem(systemItem: .flexibleSpace), done]
        inputAccessoryView = toolbar
    }
}

extension UIView {
    /// Recursively finds the currently focused text field/view within this
    /// view's hierarchy, so keyboard-avoidance code can scroll it into view
    /// without each field having to report itself individually.
    func firstResponderView() -> UIView? {
        if isFirstResponder { return self }
        for subview in subviews {
            if let match = subview.firstResponderView() { return match }
        }
        return nil
    }
}

import Foundation
import UserNotifications
import AppKit

// Usage: notifier <title> <body> <url> [group-id]
let args = CommandLine.arguments
guard args.count >= 4 else {
    fputs("Usage: notifier <title> <body> <url> [group-id]\n", stderr)
    exit(1)
}

let title     = args[1]
let body      = args[2]
let urlString = args[3]
let groupId   = args.count >= 5 ? args[4] : UUID().uuidString

// Exit after 15 min if the user never interacts with the notification.
DispatchQueue.main.asyncAfter(deadline: .now() + 900) { exit(0) }

class NotifDelegate: NSObject, UNUserNotificationCenterDelegate {

    // User clicked or dismissed the notification.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier != UNNotificationDismissActionIdentifier,
           let url = URL(string: urlString) {
            NSWorkspace.shared.open(url)
        }
        completionHandler()
        exit(0)
    }

    // Show banner even if the process is in the foreground.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound])
    }
}

let delegate = NotifDelegate()
let center   = UNUserNotificationCenter.current()
center.delegate = delegate

// Replace previous notification for the same item.
center.removeDeliveredNotifications(withIdentifiers: [groupId])
center.removePendingNotificationRequests(withIdentifiers: [groupId])

center.requestAuthorization(options: [.alert, .sound]) { granted, error in
    if let error = error {
        fputs("Authorization error: \(error)\n", stderr)
        exit(1)
    }
    guard granted else {
        fputs("Notification permission denied\n", stderr)
        exit(0)
    }

    let content        = UNMutableNotificationContent()
    content.title      = title
    content.body       = body
    content.sound      = .default

    let request = UNNotificationRequest(identifier: groupId, content: content, trigger: nil)
    center.add(request) { error in
        if let error = error {
            fputs("Failed to post notification: \(error)\n", stderr)
            exit(1)
        }
        // Stay alive — waiting for didReceive delegate callback.
    }
}

// NSApplication.shared is initialised so AppKit (NSWorkspace) works.
// RunLoop.main.run() keeps the process alive until the user clicks or
// the 15-minute timeout fires.
_ = NSApplication.shared
NSApp.setActivationPolicy(.prohibited)
RunLoop.main.run()

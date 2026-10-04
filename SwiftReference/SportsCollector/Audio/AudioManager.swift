import AVFoundation

/// Background-music playback, mirroring the original game's single-channel
/// model (`Reference/Classes/File.m`'s `MusicIds[0]`) - only one music track
/// ever plays at a time, switched between gameplay loop and win/lose jingles.
/// Gameplay sound effects are short one-shots and go through
/// `SKAction.playSoundFileNamed` directly in `GameScene`, not through here.
final class AudioManager {
    static let shared = AudioManager()

    private var musicPlayer: AVAudioPlayer?

    private init() {}

    func playMusic(_ resourceName: String, loop: Bool, fadeDuration: TimeInterval = 0.4) {
        guard let url = Bundle.main.url(forResource: resourceName, withExtension: "mp3") else { return }

        let outgoing = musicPlayer
        outgoing?.setVolume(0, fadeDuration: fadeDuration)
        if let outgoing {
            DispatchQueue.main.asyncAfter(deadline: .now() + fadeDuration) {
                outgoing.stop()
            }
        }

        guard let player = try? AVAudioPlayer(contentsOf: url) else { return }
        player.numberOfLoops = loop ? -1 : 0
        player.volume = 0
        player.prepareToPlay()
        player.play()
        player.setVolume(1, fadeDuration: fadeDuration)
        musicPlayer = player
    }

    func stopMusic(fadeDuration: TimeInterval = 0.3) {
        guard let player = musicPlayer else { return }
        player.setVolume(0, fadeDuration: fadeDuration)
        DispatchQueue.main.asyncAfter(deadline: .now() + fadeDuration) {
            player.stop()
        }
        musicPlayer = nil
    }
}

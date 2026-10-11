import SwiftUI
import XBTVCatalog

struct Poster: View {
    let url: URL?
    var width: CGFloat = 210
    var body: some View {
        AsyncImage(url: url) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            ZStack {
                LinearGradient(colors: [Color(white: 0.24), Color(white: 0.10)], startPoint: .topLeading, endPoint: .bottomTrailing)
                Image(systemName: "film").font(.largeTitle).foregroundStyle(.secondary)
            }
        }
        .frame(width: width, height: width * 1.5)
        .clipped()
        .clipShape(.rect(cornerRadius: 12))
        .accessibilityHidden(true)
    }
}

struct VideoCard: View {
    let video: Video
    var subtitle: String? = nil
    @Environment(\.isFocused) private var isFocused

    private var metadata: String {
        subtitle ?? [video.year, video.status].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Poster(url: video.coverURL, width: 250)
                .overlay(alignment: .bottom) {
                    if isFocused {
                        LinearGradient(
                            colors: [.clear, .black.opacity(0.78)],
                            startPoint: .center,
                            endPoint: .bottom
                        )
                        .clipShape(.rect(cornerRadius: 12))
                        .overlay(alignment: .bottomLeading) {
                            Text(video.title)
                                .font(.system(size: 25, weight: .semibold))
                                .lineLimit(2)
                                .minimumScaleFactor(0.85)
                                .foregroundStyle(.white)
                                .padding(.horizontal, 16)
                                .padding(.bottom, 16)
                        }
                    }
                }

            Text(isFocused ? metadata : video.title)
                .font(.system(size: 21, weight: isFocused ? .regular : .medium))
                .foregroundStyle(isFocused ? .secondary : .primary)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: 250, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }
}

struct VideoGrid: View {
    let videos: [Video]
    let client: CatalogClient
    var focus: FocusState<String?>.Binding? = nil
    @FocusState private var localFocus: String?
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 290), spacing: 36)], alignment: .leading, spacing: 44) {
            ForEach(videos) { video in
                NavigationLink(value: video) { VideoCard(video: video) }
                    .buttonStyle(.card)
                    .accessibilityHint("查看详情和剧集")
                    .focused(focus ?? $localFocus, equals: video.id)
                    .id(video.id)
            }
        }
        .focusSection()
    }
}

struct MessageView: View {
    let title: String
    let message: String
    var symbol = "exclamationmark.triangle"
    var actionTitle: String = "重试"
    var action: (() -> Void)? = nil
    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: symbol).font(.system(size: 52)).foregroundStyle(.secondary)
            Text(title).font(.title2).bold()
            Text(message).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 800)
            if let action { Button(actionTitle, action: action).buttonStyle(.borderedProminent) }
        }
        .frame(maxWidth: .infinity, minHeight: 360)
        .padding(40)
    }
}

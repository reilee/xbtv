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
        .frame(width: width, height: width * 1.45)
        .clipped()
        .clipShape(.rect(cornerRadius: 12))
        .accessibilityHidden(true)
    }
}

struct VideoCard: View {
    let video: Video
    var subtitle: String? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Poster(url: video.coverURL)
            Text(video.title).font(.headline).lineLimit(1)
            Text(subtitle ?? [video.year, video.status].filter { !$0.isEmpty }.joined(separator: " · "))
                .font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .frame(width: 210, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

struct VideoGrid: View {
    let videos: [Video]
    let client: CatalogClient
    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 230), spacing: 36)], alignment: .leading, spacing: 44) {
            ForEach(videos) { video in
                NavigationLink(value: video) { VideoCard(video: video) }
                    .buttonStyle(.card)
                    .accessibilityHint("查看详情和剧集")
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

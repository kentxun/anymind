import SwiftUI

enum TagChipSize {
    case small
    case regular

    var font: Font {
        switch self {
        case .small: return .caption
        case .regular: return .subheadline
        }
    }

    var horizontalPadding: CGFloat {
        switch self {
        case .small: return 8
        case .regular: return 10
        }
    }

    var verticalPadding: CGFloat {
        switch self {
        case .small: return 4
        case .regular: return 6
        }
    }
}

struct TagChipView: View {
    let tag: String
    var accent: Color = .blue
    var size: TagChipSize = .small

    var body: some View {
        Text(tag)
            .font(size.font)
            .padding(.horizontal, size.horizontalPadding)
            .padding(.vertical, size.verticalPadding)
            .foregroundStyle(accent)
            .background(
                Capsule()
                    .fill(accent.opacity(0.12))
            )
            .overlay(
                Capsule()
                    .stroke(accent.opacity(0.28), lineWidth: 0.8)
            )
            .clipShape(Capsule())
    }
}

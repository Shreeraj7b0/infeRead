with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# delete lines 1038 to 1095 (0-indexed: 1037 to 1095)
del lines[1037:1095]

with open('app/src/main/java/com/infer/inferead/ui/screens/FormatRenderers.kt', 'w', encoding='utf-8') as f:
    f.writelines(lines)

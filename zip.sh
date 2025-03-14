#!/bin/bash

# Répertoire courant
WORKSPACE_DIR=$(pwd)
TEMP_FILE="/tmp/code_content_$(date +%Y%m%d_%H%M%S).txt"

echo "🔍 Recherche des fichiers .ts et .java dans le workspace..."
echo "📂 Workspace: $WORKSPACE_DIR"

# Trouver tous les fichiers .ts et .java, en excluant node_modules et target
find "$WORKSPACE_DIR" \
    -type f \( -name "*.fxml" -o -name "*.java" \) \
    ! -path "*/node_modules/*" \
    ! -path "*/target/*" \
    ! -path "*/.git/*" \
    ! -path "*/dist/*" \
    ! -path "*/build/*" > "$TEMP_FILE.list"

# Compter les fichiers
FILE_COUNT=$(wc -l < "$TEMP_FILE.list")

# Vérifier si des fichiers ont été trouvés
if [ "$FILE_COUNT" -eq 0 ]; then
    echo "❌ Aucun fichier .ts ou .java trouvé dans le workspace (en excluant node_modules et target)."
    rm "$TEMP_FILE.list"
    exit 1
fi

echo "🔢 Nombre de fichiers trouvés: $FILE_COUNT"

# Initialiser le fichier temporaire
echo "" > "$TEMP_FILE"

# Pour chaque fichier, ajouter son chemin et son contenu au fichier temporaire
while IFS= read -r file; do
    echo -e "\n\n// ============================================" >> "$TEMP_FILE"
    echo -e "// FICHIER: $file" >> "$TEMP_FILE"
    echo -e "// ============================================\n" >> "$TEMP_FILE"
    cat "$file" >> "$TEMP_FILE"
done < "$TEMP_FILE.list"

# Copier le contenu dans le presse-papiers
cat "$TEMP_FILE" | pbcopy

echo "✅ Contenu copié dans le presse-papiers !"
echo "📋 $FILE_COUNT fichiers copiés (code source uniquement, pas d'images)"

# Nettoyer les fichiers temporaires
rm "$TEMP_FILE" "$TEMP_FILE.list"
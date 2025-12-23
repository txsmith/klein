package tree_sitter_klein_test

import (
	"testing"

	tree_sitter "github.com/smacker/go-tree-sitter"
	"github.com/tree-sitter/tree-sitter-klein"
)

func TestCanLoadGrammar(t *testing.T) {
	language := tree_sitter.NewLanguage(tree_sitter_klein.Language())
	if language == nil {
		t.Errorf("Error loading Klein grammar")
	}
}

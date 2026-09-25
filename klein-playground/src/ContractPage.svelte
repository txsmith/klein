<script lang="ts">
  import { checkContractSource, lineAndColumn } from "./lib/contract";

  let { source = $bindable() }: { source: string } = $props();

  const checked = $derived(checkContractSource(source));
</script>

<section class="page">
  <h2>Contract</h2>
  <textarea bind:value={source} spellcheck="false"></textarea>

  {#if checked.ok}
    <div class="summary">
      <p>
        Environment <code>{checked.contract.environment}</code>, releases
        {checked.contract.releases.join(", ") || "none"}
      </p>
      <table>
        <thead>
          <tr><th>Name</th><th>Revision</th><th>Kind</th><th>Type</th></tr>
        </thead>
        <tbody>
          {#each checked.contract.declarations as declaration (declaration.name + "/" + declaration.revision)}
            <tr>
              <td><code>{declaration.name}</code></td>
              <td>{declaration.revision}</td>
              <td>{declaration.kind}</td>
              <td><code>{declaration.type.print()}</code></td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {:else}
    <ul class="diagnostics">
      {#each checked.diagnostics as diagnostic, index (index)}
        {@const at = lineAndColumn(source, diagnostic.start)}
        <li>Line {at.line}, column {at.column}: {diagnostic.message}</li>
      {/each}
      {#each checked.messages as message, index (index)}
        <li>{message}</li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  .page {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding: 16px;
  }

  textarea {
    width: 100%;
    min-height: 360px;
    padding: 12px;
    font: 14px/1.5 var(--mono);
    color: var(--text);
    background: var(--panel);
    border: 1px solid var(--border);
    border-radius: 6px;
    resize: vertical;
    tab-size: 2;
  }

  .summary p {
    margin: 0 0 8px;
  }

  table {
    border-collapse: collapse;
  }

  th,
  td {
    padding: 4px 12px 4px 0;
    text-align: left;
    vertical-align: top;
  }

  th {
    color: var(--muted);
    font-weight: 500;
  }

  .diagnostics {
    margin: 0;
    padding-left: 20px;
    color: var(--error);
  }
</style>

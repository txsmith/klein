<script lang="ts">
  import ContractPage from "./ContractPage.svelte";
  import { lendingContract } from "./lib/examples";

  let contractSource = $state(lendingContract);
</script>

<header>
  <h1>Klein playground</h1>
</header>

<main>
  <ContractPage bind:source={contractSource} />
</main>

<style>
  header {
    padding: 12px 16px;
    border-bottom: 1px solid var(--border);
  }

  h1 {
    font-size: 18px;
  }

  main {
    max-width: 960px;
  }
</style>

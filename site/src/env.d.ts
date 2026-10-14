/// <reference types="astro/client" />
declare namespace App {
  interface Locals {
    /** The language the page being rendered is in; set by each page, read by <T> and the chrome. */
    lang: import('./i18n/langs').Lang;
  }
}

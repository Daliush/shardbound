import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CardComponent } from '../cards/card';
import { Icon } from '../core/icon';

@Component({
  selector: 'app-home-page',
  imports: [RouterLink, CardComponent, Icon],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
})
export class HomePage {}


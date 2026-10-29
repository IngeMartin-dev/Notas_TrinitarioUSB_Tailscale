import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Boletines } from './boletines';

describe('Boletines', () => {
  let component: Boletines;
  let fixture: ComponentFixture<Boletines>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Boletines]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Boletines);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});